package com.collect.worker.crawler;

import com.collect.common.mq.TaskMessage;
import com.collect.worker.es.SpiderContentDoc;
import com.collect.worker.entity.FileMetadata;
import com.collect.worker.entity.SpiderTask;
import com.collect.worker.entity.SpiderTaskLog;
import com.collect.worker.mapper.FileMetadataMapper;
import com.collect.worker.mapper.SpiderTaskLogMapper;
import com.collect.worker.mapper.SpiderTaskMapper;
import com.collect.worker.minio.MinioHelper;
import com.collect.worker.redis.UrlQueueService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import io.minio.StatObjectResponse;
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

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private final FileMetadataMapper fileMetadataMapper;

    @Value("${app.es.content-index:spider_content}")
    private String contentIndex;

    @Value("${minio.image-bucket:crawler-images}")
    private String imageBucket;

    public CrawlerEngine(SpiderTaskMapper taskMapper, SpiderTaskLogMapper logMapper,
                         ElasticsearchOperations elasticsearchOperations, UrlQueueService urlQueue,
                         MinioHelper minioHelper, FileMetadataMapper fileMetadataMapper) {
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
        this.elasticsearchOperations = elasticsearchOperations;
        this.urlQueue = urlQueue;
        this.minioHelper = minioHelper;
        this.fileMetadataMapper = fileMetadataMapper;
    }

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    @SuppressWarnings("null")
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
        Map<String, RobotsRules> robotsCache = new ConcurrentHashMap<>();

        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger fail = new AtomicInteger(0);
        boolean executorTerminated = true;

        try {
            for (String startUrl : msg.getStartUrls()) {
                String normalizedStartUrl = UrlQueueService.normalizeUrl(startUrl);
                if (normalizedStartUrl == null || normalizedStartUrl.isBlank()) continue;
                urlQueue.enqueueIfAbsent(taskId, normalizedStartUrl, 0);
            }

            while (urlQueue.size(taskId) > 0) {
                List<Runnable> batch = new java.util.ArrayList<>();
                for (int i = 0; i < concurrency; i++) {
                    String item = urlQueue.pop(taskId);
                    if (item == null) break;
                    String[] parts = item.split("\t", 2);
                    String url = parts[0];
                    if (!urlQueue.claimForProcessing(taskId, url)) continue;
                    int depth = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                        batch.add(() -> crawlUrl(url, depth, maxDepth, msg, task, taskId, success, fail, semaphore, executor,
                            robotsCache));
                }
                if (batch.isEmpty()) break;
                var futures = batch.stream().map(executor::submit).toList();
                for (var f : futures) {
                    try { f.get(); } catch (Exception ignored) {}
                }
            }
        } finally {
            executor.shutdown();
            if (!awaitExecutorTermination(executor, 30, TimeUnit.SECONDS)) {
                executorTerminated = false;
                executor.shutdownNow();
                // shutdownNow 只是发出中断请求，必须继续等待图片/页面线程真正退出。
                awaitExecutorTermination(executor, Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            }
            urlQueue.clear(taskId);
        }

        LocalDateTime endTime = LocalDateTime.now();
        SpiderTask latestTask = taskMapper.selectById(task.getId());
        if (!executorTerminated) {
            task.setStatus("FAILED");
            task.setErrorMessage("任务线程未在超时时间内结束，可能仍有页面或图片处理未完成");
        } else if (latestTask != null && "CANCELED".equals(latestTask.getStatus())) {
            task.setStatus("CANCELED");
        } else {
            task.setStatus("SUCCESS");
        }
        task.setEndTime(endTime);
        task.setTotalCostMs(task.getStartTime() == null ? 0L : java.time.Duration.between(task.getStartTime(), endTime).toMillis());
        taskMapper.updateCompletion(task.getId(), task.getStatus(), task.getErrorMessage(), endTime, task.getTotalCostMs());
        log.info("任务完成: taskId={}, totalCostMs={}", taskId, task.getTotalCostMs());
    }

    private boolean awaitExecutorTermination(java.util.concurrent.ExecutorService executor,
                                             long timeout, TimeUnit unit) {
        boolean waitIndefinitely = timeout == Long.MAX_VALUE;
        long deadline = waitIndefinitely ? 0L : System.nanoTime() + unit.toNanos(timeout);
        boolean interrupted = false;
        while (!executor.isTerminated()) {
            long remaining = waitIndefinitely ? TimeUnit.SECONDS.toNanos(1) : deadline - System.nanoTime();
            if (!waitIndefinitely && remaining <= 0) {
                if (interrupted) Thread.currentThread().interrupt();
                return false;
            }
            try {
                executor.awaitTermination(Math.min(remaining, TimeUnit.SECONDS.toNanos(1)), TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                interrupted = true;
                executor.shutdownNow();
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
        return true;
    }

    private void crawlUrl(String url, int depth, int maxDepth, TaskMessage msg, SpiderTask task,
                          Long taskId, AtomicInteger success, AtomicInteger fail,
                          java.util.concurrent.Semaphore semaphore,
                          java.util.concurrent.ExecutorService executor,
                          Map<String, RobotsRules> robotsCache) {
        try {
            semaphore.acquire();
            try {
                if (!isAllowedByRobots(url, msg, robotsCache)) {
                    log.info("robots.txt 禁止抓取: url={}", url);
                    writeLog(task.getId(), msg.getSpiderId(), url, 0, "ERROR", "robots.txt 禁止抓取", 0);
                    fail.incrementAndGet();
                    return;
                }
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
                    success.incrementAndGet();
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
                // 图片：不覆盖时跳过已存在的图片，覆盖时重新下载
                List<String> imageUrls = extractAndUploadImages(doc, url, parsed.getTitle(), msg, task, overwriteImage);
                docObj.setImages(imageUrls);

                saveToElasticsearchWithRetry(docObj);
                writeLog(task.getId(), msg.getSpiderId(), url, 1, "INFO",
                    (overwriteHtml && existingDoc != null) ? "覆盖更新: " + parsed.getTitle() : "抓取成功: " + parsed.getTitle(), (int) cost);

                if (!msg.isSingleUrl() && depth < maxDepth) {
                    List<String> next = ContentParser.extractNextUrls(doc, url, maxDepth - depth);
                    next.removeIf(u -> {
                        String normalized = UrlQueueService.normalizeUrl(u);
                        return normalized == null || msg.getStartUrls().stream()
                                .map(UrlQueueService::normalizeUrl)
                                .anyMatch(normalized::equals) || !isSameDomain(normalized, msg.getStartUrls());
                    });
                    int enqueued = 0;
                    for (String nextUrl : next) {
                        String normalizedNextUrl = UrlQueueService.normalizeUrl(nextUrl);
                        if (normalizedNextUrl == null || normalizedNextUrl.isBlank()) continue;
                        if (!isAllowedByRobots(normalizedNextUrl, msg, robotsCache)) continue;
                        if (urlQueue.enqueueIfAbsent(taskId, normalizedNextUrl, depth + 1)) enqueued++;
                    }
                    log.info("depth={}, url={}, extracted={}, enqueued={}", depth, url, next.size(), enqueued);
                }
                success.incrementAndGet();
            } finally {
                semaphore.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail.incrementAndGet();
            writeLog(task.getId(), msg.getSpiderId(), url, 0, "ERROR", "任务线程被中断", 0);
        } catch (Exception e) {
            fail.incrementAndGet();
            log.warn("抓取失败: {}", url, e);
            writeLog(task.getId(), msg.getSpiderId(), url, 0, "ERROR", e.getMessage(), 0);
        }
    }

    private void saveToElasticsearchWithRetry(SpiderContentDoc document) throws InterruptedException {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                elasticsearchOperations.save(document, IndexCoordinates.of(contentIndex));
                return;
            } catch (org.springframework.dao.DataAccessResourceFailureException exception) {
                if (!isClosedConnection(exception) || attempt == 3) {
                    throw exception;
                }
                log.warn("Elasticsearch 连接已关闭，准备重试写入: id={}, attempt={}", document.getId(), attempt);
                Thread.sleep(attempt * 500L);
            }
        }
    }

    private boolean isClosedConnection(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.apache.http.ConnectionClosedException
                    || cause.getMessage() != null && cause.getMessage().contains("Connection closed unexpectedly")) {
                return true;
            }
        }
        return false;
    }

    private boolean isSameDomain(String url, List<String> startUrls) {
        try {
            URI target = URI.create(url);
            String targetHost = normalizeHost(target.getHost());
            if (targetHost == null) return false;
            for (String startUrl : startUrls) {
                URI start = URI.create(startUrl);
                if (targetHost.equals(normalizeHost(start.getHost()))) return true;
            }
        } catch (Exception e) {
            log.debug("URL 域名解析失败: {}", url);
        }
        return false;
    }

    private String normalizeHost(String host) {
        if (host == null || host.isBlank()) return null;
        String normalized = host.toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("www.") ? normalized.substring(4) : normalized;
    }

    private boolean isAllowedByRobots(String url, TaskMessage msg, Map<String, RobotsRules> robotsCache) {
        if (!Integer.valueOf(1).equals(msg.getFollowRobots())) return true;
        try {
            URI uri = URI.create(url);
            if (uri.getHost() == null) return false;
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
            String robotsUrl = scheme + "://" + uri.getAuthority() + "/robots.txt";
            RobotsRules rules = robotsCache.computeIfAbsent(robotsUrl, this::loadRobotsRules);
            return rules.isAllowed(uri.getRawPath());
        } catch (Exception e) {
            log.debug("robots.txt 检查失败，放行 URL: {}", url, e);
            return true;
        }
    }

    private RobotsRules loadRobotsRules(String robotsUrl) {
        try {
            Request request = new Request.Builder()
                    .url(robotsUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CollectX/1.0")
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return RobotsRules.allowAll();
                return RobotsRules.parse(response.body().string());
            }
        } catch (Exception e) {
            log.debug("读取 robots.txt 失败，放行 URL: {}", robotsUrl);
            return RobotsRules.allowAll();
        }
    }

    private static final class RobotsRules {
        private final List<String> disallowedPaths;

        private RobotsRules(List<String> disallowedPaths) {
            this.disallowedPaths = disallowedPaths;
        }

        static RobotsRules allowAll() {
            return new RobotsRules(List.of());
        }

        static RobotsRules parse(String content) {
            List<String> disallowed = new java.util.ArrayList<>();
            boolean appliesToOurAgent = false;
            boolean hasUserAgent = false;
            for (String rawLine : content.split("\\R")) {
                String line = rawLine.split("#", 2)[0].trim();
                if (line.isEmpty() || !line.contains(":")) continue;
                String[] pair = line.split(":", 2);
                String key = pair[0].trim().toLowerCase(java.util.Locale.ROOT);
                String value = pair[1].trim();
                if ("user-agent".equals(key)) {
                    hasUserAgent = true;
                    appliesToOurAgent = "*".equals(value)
                            || value.toLowerCase(java.util.Locale.ROOT).contains("collectx");
                } else if ("disallow".equals(key) && appliesToOurAgent && !value.isEmpty()) {
                    disallowed.add(value);
                }
            }
            return hasUserAgent ? new RobotsRules(disallowed) : allowAll();
        }

        boolean isAllowed(String path) {
            String normalizedPath = path == null || path.isEmpty() ? "/" : path;
            return disallowedPaths.stream().noneMatch(normalizedPath::startsWith);
        }
    }

    /**
     * 若爬虫配置了图片 CSS 选择器，则提取选择器命中的元素内的图片并上传到 MinIO。
     * overwrite=true 时重新下载并覆盖已存在的图片；否则跳过已存在的图片。
     * 返回已上传图片的 objectName 列表（无图片时返回空列表）。
     */
    private List<String> extractAndUploadImages(Document doc, String pageUrl, String title,
                                                TaskMessage msg, SpiderTask task, boolean overwrite) {
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
                        saveExistingFileMetadata(objectName, msg.getSpiderId(), title, pageUrl);
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
                            saveExistingFileMetadata(objectName, msg.getSpiderId(), title, pageUrl);
                            uploadedUrls.add(objectName);
                            continue;
                        }
                    }
                    // 覆盖模式下 putObject 会直接覆盖已存在的对象
                    minioHelper.putImage(imageBucket, objectName, data, guessContentType(realExt));
                    saveFileMetadata(objectName, data.length, guessContentType(realExt), msg.getSpiderId(), title, pageUrl);
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
                writeLog(task.getId(), msg.getSpiderId(), pageUrl, skipped > 0 ? 2 : 1, "INFO", msg2, (int) imgCost, "image");
            }
        } catch (Exception e) {
            log.warn("图片提取失败: url={}", pageUrl, e);
        }
        return uploadedUrls;
    }

    private void saveFileMetadata(String objectName, long fileSize, String contentType, Long spiderId,
                                  String title, String source) {
        if (fileMetadataMapper.selectByObject(imageBucket, objectName) != null) {
            return;
        }
        FileMetadata metadata = new FileMetadata();
        metadata.setBucket(imageBucket);
        metadata.setObjectName(objectName);
        metadata.setFileName(objectName.substring(objectName.lastIndexOf('/') + 1));
        metadata.setTitle(title);
        metadata.setContentType(contentType);
        metadata.setFileSize(fileSize);
        metadata.setCategory("image");
        metadata.setSpiderId(spiderId);
        metadata.setSource(source);
        fileMetadataMapper.insert(metadata);
    }

    private void saveExistingFileMetadata(String objectName, Long spiderId, String title, String source) {
        if (fileMetadataMapper.selectByObject(imageBucket, objectName) != null) {
            return;
        }
        try {
            StatObjectResponse stat = minioHelper.statObject(imageBucket, objectName);
            saveFileMetadata(objectName, stat.size(), stat.contentType(), spiderId, title, source);
        } catch (Exception e) {
            log.warn("读取已有文件元数据失败: bucket={}, object={}", imageBucket, objectName, e);
        }
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
        writeLog(taskId, spiderId, url, status, level, message, costMs, "html");
    }

    private void writeLog(Long taskId, Long spiderId, String url, int status,
                          String level, String message, int costMs, String type) {
        SpiderTaskLog logEntry = new SpiderTaskLog();
        logEntry.setTaskId(taskId);
        logEntry.setSpiderId(spiderId);
        logEntry.setUrl(url);
        logEntry.setStatus(status);
        logEntry.setLevel(level);
        logEntry.setType(type);
        logEntry.setMessage(message != null && message.length() > 500 ? message.substring(0, 500) : message);
        logEntry.setCostMs(costMs);
        logMapper.insert(logEntry);
    }
}
