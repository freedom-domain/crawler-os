package com.collect.worker.crawler;

import com.collect.common.mq.TaskMessage;
import com.collect.worker.es.SpiderContentDoc;
import com.collect.worker.entity.SpiderTask;
import com.collect.worker.entity.SpiderTaskLog;
import com.collect.worker.mapper.SpiderTaskLogMapper;
import com.collect.worker.mapper.SpiderTaskMapper;
import com.collect.worker.minio.MinioHelper;
import com.collect.worker.redis.UrlQueueService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class CrawlerEngine {

    private final SpiderTaskMapper taskMapper;
    private final SpiderTaskLogMapper logMapper;
    private final ElasticsearchOperations elasticsearchOperations;
    private final UrlQueueService urlQueue;
    private final MinioHelper minioHelper;

    @Value("${app.es.content-index:spider_content}")
    private String contentIndex;

    @Value("${minio.image-bucket:crawler-images}")
    private String imageBucket;

    public CrawlerEngine(SpiderTaskMapper taskMapper, SpiderTaskLogMapper logMapper,
                         ElasticsearchOperations elasticsearchOperations, UrlQueueService urlQueue,
                         MinioHelper minioHelper) {
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
        this.elasticsearchOperations = elasticsearchOperations;
        this.urlQueue = urlQueue;
        this.minioHelper = minioHelper;
    }

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    public void execute(TaskMessage msg) {
        SpiderTask task = taskMapper.selectByTaskId(msg.getTaskId());
        if (task == null) {
            log.warn("任务不存在: {}", msg.getTaskId());
            return;
        }

        Long taskId = msg.getTaskId();
        int maxDepth = msg.getMaxDepth() != null ? msg.getMaxDepth() : 2;
        int concurrency = 8;
        var semaphore = new java.util.concurrent.Semaphore(concurrency);
        var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger fail = new AtomicInteger(0);

        try {
            for (String startUrl : msg.getStartUrls()) {
                if (urlQueue.isVisited(taskId, startUrl)) continue;
                urlQueue.markVisited(taskId, startUrl);
                urlQueue.push(taskId, startUrl + "\t0");
            }

            while (urlQueue.size(taskId) > 0) {
                List<Runnable> batch = new java.util.ArrayList<>();
                for (int i = 0; i < concurrency; i++) {
                    String item = urlQueue.pop(taskId);
                    if (item == null) break;
                    String[] parts = item.split("\t", 2);
                    String url = parts[0];
                    int depth = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                    batch.add(() -> crawlUrl(url, depth, maxDepth, msg, task, taskId, success, fail, semaphore, executor));
                }
                if (batch.isEmpty()) break;
                var futures = batch.stream().map(executor::submit).toList();
                for (var f : futures) {
                    try { f.get(); } catch (Exception ignored) {}
                }
            }
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
            urlQueue.clear(taskId);
        }

        LocalDateTime endTime = LocalDateTime.now();
        taskMapper.setSuccess(task.getId(), success.get());
        taskMapper.setFail(task.getId(), fail.get());
        task.setSuccessCount(success.get());
        task.setFailCount(fail.get());
        task.setStatus("SUCCESS");
        task.setEndTime(endTime);
        task.setTotalCostMs(task.getStartTime() == null ? 0L : java.time.Duration.between(task.getStartTime(), endTime).toMillis());
        taskMapper.updateById(task);
        log.info("任务完成: taskId={}, success={}, fail={}, totalCostMs={}", taskId, success.get(), fail.get(), task.getTotalCostMs());
    }

    private void crawlUrl(String url, int depth, int maxDepth, TaskMessage msg, SpiderTask task,
                          Long taskId, AtomicInteger success, AtomicInteger fail,
                          java.util.concurrent.Semaphore semaphore,
                          java.util.concurrent.ExecutorService executor) {
        try {
            semaphore.acquire();
            try {
                long start = System.currentTimeMillis();
                Document doc = fetch(url, msg.getTimeout());
                long cost = System.currentTimeMillis() - start;

                ContentParser parsed = ContentParser.parse(doc.outerHtml(), url, msg.getContentSelector());
                String newHtml = doc.outerHtml();
                String newHtmlHash = md5(newHtml);
                boolean overwriteHtml = msg.getOverwriteHtml() != null && msg.getOverwriteHtml() == 1;
                boolean overwriteImage = msg.getOverwriteImage() != null && msg.getOverwriteImage() == 1;

                // 查询该 URL 是否已存在
                CriteriaQuery criteriaQuery = new CriteriaQuery(new Criteria("url").is(url));
                SearchHits<SpiderContentDoc> existing = elasticsearchOperations.search(
                        criteriaQuery, SpiderContentDoc.class, IndexCoordinates.of(contentIndex));
                SpiderContentDoc existingDoc = existing.isEmpty() ? null : existing.getSearchHits().get(0).getContent();
                boolean contentUnchanged = false;
                for (SearchHit<SpiderContentDoc> hit : existing) {
                    SpiderContentDoc d = hit.getContent();
                    if (newHtmlHash.equals(md5(d.getRawHtml() != null ? d.getRawHtml() : ""))) {
                        contentUnchanged = true;
                        break;
                    }
                }

                // 不覆盖HTML 且 内容未变化 → 跳过
                if (!overwriteHtml && contentUnchanged) {
                    writeLog(task.getId(), msg.getSpiderId(), url, 2, "INFO",
                            "已存在，跳过: " + parsed.getTitle(), (int) cost);
                    log.info("内容未变化，跳过: url={}", url);
                    return;
                }

                SpiderContentDoc docObj = new SpiderContentDoc();
                docObj.setId(md5(url));
                docObj.setTitle(parsed.getTitle());
                docObj.setContent(parsed.getContent());
                docObj.setUrl(url);
                docObj.setAuthor(parsed.getAuthor());
                docObj.setSpiderId(msg.getSpiderId());
                docObj.setSpiderName(msg.getSpiderName());
                docObj.setSourceType(msg.getType());
                DateTimeFormatter esDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
                docObj.setCrawlTime(LocalDateTime.now().format(esDateFormatter));
                docObj.setUpdateTime(LocalDateTime.now().format(esDateFormatter));
                docObj.setRawHtml(newHtml);
                if (existingDoc != null) {
                    docObj.setTags(existingDoc.getTags());
                }

                // 图片：不覆盖时跳过已存在的图片，覆盖时重新下载
                List<String> imageUrls = extractAndUploadImages(doc, url, msg, task, overwriteImage);
                docObj.setImages(imageUrls);

                elasticsearchOperations.save(docObj, IndexCoordinates.of(contentIndex));
                success.incrementAndGet();
                writeLog(task.getId(), msg.getSpiderId(), url, 1, "INFO",
                    (overwriteHtml && existingDoc != null) ? "覆盖更新: " + parsed.getTitle() : "抓取成功: " + parsed.getTitle(), (int) cost);

                if (!msg.isSingleUrl() && depth < maxDepth) {
                    List<String> next = ContentParser.extractNextUrls(doc, url, maxDepth - depth);
                    next.removeIf(u -> msg.getStartUrls().contains(u));
                    int enqueued = 0;
                    for (String nextUrl : next) {
                        if (urlQueue.isVisited(taskId, nextUrl)) continue;
                        urlQueue.markVisited(taskId, nextUrl);
                        urlQueue.push(taskId, nextUrl + "\t" + (depth + 1));
                        enqueued++;
                    }
                    log.info("depth={}, url={}, extracted={}, enqueued={}", depth, url, next.size(), enqueued);
                }
            } finally {
                semaphore.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            fail.incrementAndGet();
            log.warn("抓取失败: {}", url, e);
            writeLog(task.getId(), msg.getSpiderId(), url, 0, "ERROR", e.getMessage(), 0);
        }
    }

    /**
     * 若爬虫配置了图片 CSS 选择器，则提取选择器命中的元素内的图片并上传到 MinIO。
     * overwrite=true 时重新下载并覆盖已存在的图片；否则跳过已存在的图片。
     * 返回已上传图片的 objectName 列表（无图片时返回空列表）。
     */
    private List<String> extractAndUploadImages(Document doc, String pageUrl, TaskMessage msg, SpiderTask task, boolean overwrite) {
        String selector = msg.getImageSelector();
        if (selector == null || selector.isBlank()) {
            return List.of();
        }
        List<String> uploadedUrls = new java.util.ArrayList<>();
        long imgStart = System.currentTimeMillis();
        try {
            Elements matched = doc.select(selector);
            if (matched.isEmpty()) {
                log.info("页面未匹配到图片选择器: url={}, selector={}", pageUrl, selector);
                return uploadedUrls;
            }
            // 收集选择器命中的元素本身（若是 img）以及其内部的所有 img
            List<Element> imgs = new java.util.ArrayList<>();
            for (Element el : matched) {
                if ("img".equalsIgnoreCase(el.tagName())) {
                    imgs.add(el);
                }
                imgs.addAll(el.select("img"));
            }

            int uploaded = 0;
            int skipped = 0;
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (Element img : imgs) {
                String src = img.absUrl("src");
                if (src.isBlank() || !seen.add(src)) {
                    continue;
                }
                try {
                    // 先用 URL 的 MD5 推算对象名
                    String ext = guessExt(src, null);
                    String objectName = "spider/" + msg.getSpiderId() + "/" + md5(src) + ext;
                    // 不覆盖时，若图片已存在则跳过下载
                    if (!overwrite && minioHelper.objectExists(imageBucket, objectName)) {
                        skipped++;
                        uploadedUrls.add(objectName);
                        continue;
                    }
                    byte[] data = downloadImage(src);
                    if (data == null || data.length == 0) {
                        continue;
                    }
                    // 下载后若扩展名与魔数判断不一致，则用实际扩展名重新命名
                    String realExt = guessExt(src, data);
                    if (!realExt.equals(ext)) {
                        objectName = "spider/" + msg.getSpiderId() + "/" + md5(src) + realExt;
                        if (!overwrite && minioHelper.objectExists(imageBucket, objectName)) {
                            skipped++;
                            uploadedUrls.add(objectName);
                            continue;
                        }
                    }
                    // 覆盖模式下 putObject 会直接覆盖已存在的对象
                    minioHelper.putImage(imageBucket, objectName, data, guessContentType(realExt));
                    uploaded++;
                    // 仅存储 MinIO 相对路径（objectName），前端通过后端接口按 objectName 获取图片
                    uploadedUrls.add(objectName);
                } catch (Exception e) {
                    log.warn("图片下载/上传失败: src={}", src, e);
                }
            }
            long imgCost = System.currentTimeMillis() - imgStart;
            if (uploaded > 0 || skipped > 0) {
                String msg2 = skipped > 0
                        ? "图片: 上传 " + uploaded + " 张, 已存在跳过 " + skipped + " 张, 耗时 " + imgCost + "ms"
                        : "图片上传: " + uploaded + " 张, 耗时 " + imgCost + "ms";
                log.info("图片处理完成: url={}, uploaded={}, skipped={}, cost={}ms", pageUrl, uploaded, skipped, imgCost);
                writeLog(task.getId(), msg.getSpiderId(), pageUrl, 1, "INFO", msg2, (int) imgCost);
            }
        } catch (Exception e) {
            log.warn("图片提取失败: url={}", pageUrl, e);
        }
        return uploadedUrls;
    }

    private byte[] downloadImage(String src) throws Exception {
        Request request = new Request.Builder()
                .url(src)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CollectX/1.0")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            return response.body().bytes();
        }
    }

    private String guessExt(String src, byte[] data) {
        String lower = src.toLowerCase();
        int q = lower.indexOf('?');
        if (q > 0) {
            lower = lower.substring(0, q);
        }
        String ext = "";
        int dot = lower.lastIndexOf('.');
        if (dot >= 0 && dot < lower.length() - 1) {
            ext = lower.substring(dot);
        }
        if (ext.isEmpty()) {
            // 通过魔数判断
            if (data.length > 3 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
                ext = ".png";
            } else if (data.length > 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
                ext = ".jpg";
            } else if (data.length > 5 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
                ext = ".gif";
            } else if (data.length > 8 && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
                ext = ".webp";
            } else {
                ext = ".img";
            }
        }
        return ext;
    }

    private String guessContentType(String ext) {
        return switch (ext) {
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            case ".bmp" -> "image/bmp";
            case ".svg" -> "image/svg+xml";
            default -> "application/octet-stream";
        };
    }

    private Document fetch(String url, Integer timeout) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CollectX/1.0")
                .header("Accept", "text/html,application/xhtml+xml")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("HTTP " + response.code());
            }
            return Jsoup.parse(response.body().string(), url);
        }
    }

    private String md5(String input) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("MD5")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return java.util.UUID.randomUUID().toString().replace("-", "");
        }
    }

    private void writeLog(Long taskId, Long spiderId, String url, int status,
                          String level, String message, int costMs) {
        SpiderTaskLog logEntry = new SpiderTaskLog();
        logEntry.setTaskId(taskId);
        logEntry.setSpiderId(spiderId);
        logEntry.setUrl(url);
        logEntry.setStatus(status);
        logEntry.setLevel(level);
        logEntry.setMessage(message != null && message.length() > 500 ? message.substring(0, 500) : message);
        logEntry.setCostMs(costMs);
        logMapper.insert(logEntry);
    }
}
