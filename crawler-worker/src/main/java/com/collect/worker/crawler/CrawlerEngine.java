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
import java.util.Set;
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

    @Value("${minio.image-bucket:crawler}")
    private String imageBucket;

    @Value("${minio.html-bucket:crawler}")
    private String htmlBucket;

    @Value("${minio.js-bucket:crawler}")
    private String jsBucket;

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
        Set<String> processedResourceUrls = ConcurrentHashMap.newKeySet();
        boolean executorTerminated = true;

        try {
            for (String startUrl : msg.getStartUrls()) {
                String normalizedStartUrl = UrlQueueService.normalizeUrl(startUrl);
                if (normalizedStartUrl == null || normalizedStartUrl.isBlank()) continue;
                urlQueue.enqueueIfAbsent(taskId, normalizedStartUrl, 0);
            }

            while (urlQueue.size(taskId) > 0) {
                // 任务状态不是运行中（如被取消）时停止爬取
                SpiderTask latest = taskMapper.selectById(task.getId());
                if (latest == null || !"RUNNING".equals(latest.getStatus())) {
                    log.info("任务状态不是运行中，停止爬取: taskId={}, status={}", taskId, latest == null ? null : latest.getStatus());
                    break;
                }
                List<Runnable> batch = new java.util.ArrayList<>();
                for (int i = 0; i < concurrency; i++) {
                    String item = urlQueue.pop(taskId);
                    if (item == null) break;
                    String[] parts = item.split("\t", 2);
                    String url = parts[0];
                    if (!urlQueue.claimForProcessing(taskId, url)) continue;
                    int depth = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                        batch.add(() -> crawlUrl(url, depth, maxDepth, msg, task, taskId, success, fail, semaphore, executor,
                            robotsCache, processedResourceUrls));
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
        } else if (latestTask != null
                && ("CANCELED".equals(latestTask.getStatus()) || "CANCELING".equals(latestTask.getStatus()))) {
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
                          Map<String, RobotsRules> robotsCache,
                          Set<String> processedResourceUrls) {
        try {
            semaphore.acquire();
            try {
                // 任务状态不是运行中（如被取消）时停止爬取
                SpiderTask latest = taskMapper.selectById(task.getId());
                if (latest == null || !"RUNNING".equals(latest.getStatus())) {
                    log.info("任务状态不是运行中，停止爬取: taskId={}, url={}", taskId, url);
                    return;
                }
                if (!isAllowedByRobots(url, msg, robotsCache)) {
                    log.info("robots.txt 禁止抓取: url={}", url);
                    writeLog(task.getId(), msg.getSpiderId(), url, 0, "ERROR", "robots.txt 禁止抓取", 0);
                    fail.incrementAndGet();
                    return;
                }
                long start = System.currentTimeMillis();
                boolean readCache = Integer.valueOf(1).equals(msg.getReadCache());
                String cachedHtml = readCache
                        ? minioHelper.getHtmlIfExists(htmlBucket, "html/" + md5(url) + ".html")
                        : null;
                boolean cacheHit = cachedHtml != null;
                Document doc = cacheHit ? Jsoup.parse(cachedHtml, url) : fetch(url, msg.getTimeout());
                long cost = System.currentTimeMillis() - start;

                ContentParser parsed = ContentParser.parse(doc.outerHtml(), url, msg.getContentSelector());
                String newHtml = doc.outerHtml();
                String newHtmlHash = md5(newHtml);
                boolean overwriteHtml = cacheHit || Integer.valueOf(1).equals(msg.getOverwriteHtml());
                boolean overwriteImage = !cacheHit && Integer.valueOf(1).equals(msg.getOverwriteImage());

                boolean contentUnchanged = false;
                SpiderContentDoc existingDoc = null;
                if (!isConfiguredStartUrl(url, msg.getStartUrls())) {
                    // 配置中的起始 URL 始终抓取；仅其他页面检查已有内容是否需要跳过。
                    CriteriaQuery criteriaQuery = new CriteriaQuery(new Criteria("url").is(url));
                    SearchHits<SpiderContentDoc> existing = elasticsearchOperations.search(
                            criteriaQuery, SpiderContentDoc.class, IndexCoordinates.of(contentIndex));
                    existingDoc = existing.isEmpty() ? null : existing.getSearchHits().get(0).getContent();
                    if (existingDoc != null && !cacheHit) {
                        // HTML 原文已迁移到 MinIO，从 MinIO 读取旧内容做变更比对
                        String oldHtml = readHtmlFromMinio(url);
                        if (oldHtml != null && newHtmlHash.equals(md5(oldHtml))) {
                            contentUnchanged = true;
                        }
                    }
                }

                // 后续页面不覆盖 HTML 且内容未变化 → 跳过
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
                // HTML 原文不再写入 ES，统一存入 MinIO（见 saveHtmlAndJs）
                // 图片：不覆盖时跳过已存在的图片，覆盖时重新下载
                List<String> imageUrls = shouldSkipImageDownload(doc, msg)
                        ? List.of()
                        : extractAndUploadImages(doc, url, parsed.getTitle(), msg, task, overwriteImage);
                docObj.setImages(imageUrls);

                // HTML 原文存入 html 目录，页面引用的 JS 存入 js 目录
                saveHtmlAndJs(doc, url, newHtml, msg, task, !cacheHit && overwriteHtml,
                        !cacheHit, processedResourceUrls);

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

    private boolean isConfiguredStartUrl(String url, List<String> startUrls) {
        String normalizedUrl = UrlQueueService.normalizeUrl(url);
        return normalizedUrl != null && startUrls != null && startUrls.stream()
                .map(UrlQueueService::normalizeUrl)
                .anyMatch(normalizedUrl::equals);
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
                    // 先用 URL 的 MD5 推算对象名（统一存入 images 前缀下）
                    String ext = guessExt(src, null);
                    String objectName = "images/" + md5(src) + ext;
                    // 不覆盖时，若图片已存在则跳过下载
                    if (!overwrite && minioHelper.objectExists(imageBucket, objectName)) {
                        skipped++;
                        saveExistingFileMetadata(objectName, msg.getSpiderId(), title, src);
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
                        objectName = "images/" + md5(src) + realExt;
                        if (!overwrite && minioHelper.objectExists(imageBucket, objectName)) {
                            skipped++;
                            saveExistingFileMetadata(objectName, msg.getSpiderId(), title, src);
                            uploadedUrls.add(objectName);
                            continue;
                        }
                    }
                    // 覆盖模式下 putObject 会直接覆盖已存在的对象
                    minioHelper.putImage(imageBucket, objectName, data, guessContentType(realExt));
                        saveFileMetadata(imageBucket, objectName, data.length, guessContentType(realExt), "image",
                            msg.getSpiderId(), title, pageUrl);
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

    private boolean shouldSkipImageDownload(Document doc, TaskMessage msg) {
        boolean matched = ContentParser.matchesSelectorContent(
                doc, msg.getVipSelector(), msg.getVipSelectorContent());
        if (matched) {
            log.info("页面匹配 VIP 选择器内容，跳过图片下载: selector={}, content={}",
                    msg.getVipSelector(), msg.getVipSelectorContent());
        }
        return matched;
    }

    /**
     * 按需上传页面 HTML 原文，并将页面引用的 JS/CSS 文件上传到资源目录。
     * 对象名基于 URL 的 MD5；资源是否覆盖与 HTML 使用同一个覆盖开关。
     */
    private void saveHtmlAndJs(Document doc, String url, String html, TaskMessage msg, SpiderTask task,
                         boolean overwriteResources, boolean saveHtml, Set<String> processedResourceUrls) {
        try {
            String urlHash = md5(url);
            if (saveHtml) {
                String htmlObject = "html/" + urlHash + ".html";
                minioHelper.putHtml(htmlBucket, htmlObject, html);
                saveFileMetadata(htmlBucket, htmlObject, html.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                        "text/html; charset=utf-8", "html", msg.getSpiderId(), parsedTitle(doc, url), url);
            }
            // 页面引用的 JS 文件
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (Element script : doc.select("script[src]")) {
                String jsUrl = script.absUrl("src");
                if (jsUrl.isBlank() || !seen.add(jsUrl)) {
                    continue;
                }
                long resourceStart = System.currentTimeMillis();
                String resourceKey = "js:" + jsUrl;
                if (!processedResourceUrls.add(resourceKey)) {
                    writeLog(task.getId(), msg.getSpiderId(), jsUrl, 2, "INFO", "JS URL 已处理，跳过: " + jsUrl,
                            resourceCost(resourceStart), "js");
                    continue;
                }
                try {
                    byte[] data = downloadBytes(jsUrl);
                    if (data == null || data.length == 0) {
                        processedResourceUrls.remove(resourceKey);
                        writeLog(task.getId(), msg.getSpiderId(), jsUrl, 0, "ERROR", "JS 下载为空", resourceCost(resourceStart), "js");
                        continue;
                    }
                    String jsObject = "js/" + md5(jsUrl) + guessExt(jsUrl, data);
                    if (!overwriteResources && minioHelper.objectExists(jsBucket, jsObject)) {
                        writeLog(task.getId(), msg.getSpiderId(), jsUrl, 2, "INFO", "JS 已存在，跳过上传: " + jsObject,
                                resourceCost(resourceStart), "js");
                        continue;
                    }
                    minioHelper.putJs(jsBucket, jsObject, data);
                        saveFileMetadata(jsBucket, jsObject, data.length, "application/javascript", "js",
                            msg.getSpiderId(), parsedTitle(doc, url), jsUrl);
                    writeLog(task.getId(), msg.getSpiderId(), jsUrl, 1, "INFO", "JS 上传成功: " + jsObject,
                            resourceCost(resourceStart), "js");
                } catch (Exception e) {
                    processedResourceUrls.remove(resourceKey);
                    log.warn("JS 下载/上传失败: src={}", jsUrl, e);
                    writeLog(task.getId(), msg.getSpiderId(), jsUrl, 0, "ERROR", "JS 下载/上传失败: " + e.getMessage(),
                            resourceCost(resourceStart), "js");
                }
            }
            // 页面引用的 CSS 文件，与 JS 使用相同的资源桶但使用独立的 css/ 前缀
            seen.clear();
            for (Element stylesheet : doc.select("link[href]")) {
                String rel = stylesheet.attr("rel");
                if (java.util.Arrays.stream(rel.split("\\s+"))
                        .noneMatch(value -> "stylesheet".equalsIgnoreCase(value))) {
                    continue;
                }
                String cssUrl = stylesheet.absUrl("href");
                if (cssUrl.isBlank() || !seen.add(cssUrl)) {
                    continue;
                }
                long resourceStart = System.currentTimeMillis();
                String resourceKey = "css:" + cssUrl;
                if (!processedResourceUrls.add(resourceKey)) {
                    writeLog(task.getId(), msg.getSpiderId(), cssUrl, 2, "INFO", "CSS URL 已处理，跳过: " + cssUrl,
                            resourceCost(resourceStart), "css");
                    continue;
                }
                try {
                    byte[] data = downloadBytes(cssUrl);
                    if (data == null || data.length == 0) {
                        processedResourceUrls.remove(resourceKey);
                        writeLog(task.getId(), msg.getSpiderId(), cssUrl, 0, "ERROR", "CSS 下载为空", resourceCost(resourceStart), "css");
                        continue;
                    }
                    String cssObject = "css/" + md5(cssUrl) + ".css";
                    if (!overwriteResources && minioHelper.objectExists(jsBucket, cssObject)) {
                        writeLog(task.getId(), msg.getSpiderId(), cssUrl, 2, "INFO", "CSS 已存在，跳过上传: " + cssObject,
                                resourceCost(resourceStart), "css");
                        continue;
                    }
                    minioHelper.putCss(jsBucket, cssObject, data);
                        saveFileMetadata(jsBucket, cssObject, data.length, "text/css", "css",
                            msg.getSpiderId(), parsedTitle(doc, url), cssUrl);
                    writeLog(task.getId(), msg.getSpiderId(), cssUrl, 1, "INFO", "CSS 上传成功: " + cssObject,
                            resourceCost(resourceStart), "css");
                } catch (Exception e) {
                    processedResourceUrls.remove(resourceKey);
                    log.warn("CSS 下载/上传失败: href={}", cssUrl, e);
                    writeLog(task.getId(), msg.getSpiderId(), cssUrl, 0, "ERROR", "CSS 下载/上传失败: " + e.getMessage(),
                            resourceCost(resourceStart), "css");
                }
            }
        } catch (Exception e) {
            log.warn("HTML/JS/CSS 上传失败: url={}", url, e);
        }
    }

    private String parsedTitle(Document doc, String url) {
        String title = doc.title();
        return title.isBlank() ? url : title;
    }

    private int resourceCost(long startTime) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, System.currentTimeMillis() - startTime));
    }

    /**
     * 从 MinIO 读取已存储的 HTML 原文（用于变更比对）。对象不存在或读取失败时返回 null。
     */
    private String readHtmlFromMinio(String url) {
        try {
            String objectName = "html/" + md5(url) + ".html";
            try (java.io.InputStream in = minioHelper.getObject(htmlBucket, objectName)) {
                return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.debug("从 MinIO 读取 HTML 失败: url={}", url, e);
            return null;
        }
    }

    private byte[] downloadBytes(String src) throws Exception {
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

    private void saveFileMetadata(String bucket, String objectName, long fileSize, String contentType,
                                  String category, Long spiderId, String title, String source) {
        if (fileMetadataMapper.selectByObject(bucket, objectName) != null
                || (source != null && !source.isBlank() && fileMetadataMapper.selectBySource(source) != null)) {
            return;
        }
        FileMetadata metadata = new FileMetadata();
        metadata.setBucket(bucket);
        metadata.setObjectName(objectName);
        metadata.setFileName(objectName.substring(objectName.lastIndexOf('/') + 1));
        metadata.setTitle(title);
        metadata.setContentType(contentType);
        metadata.setFileSize(fileSize);
        metadata.setCategory(category);
        metadata.setSpiderId(spiderId);
        metadata.setSource(source);
        fileMetadataMapper.insert(metadata);
    }

    /**
    * 根据对象路径前缀推断文件类别：images → image，html → html，js → js，css → css。
     */
    private String inferCategory(String objectName) {
        if (objectName.startsWith("html/")) return "html";
        if (objectName.startsWith("js/")) return "js";
        if (objectName.startsWith("css/")) return "css";
        if (objectName.startsWith("images/")) return "image";
        return "file";
    }

    private void saveExistingFileMetadata(String objectName, Long spiderId, String title, String source) {
        if (fileMetadataMapper.selectByObject(imageBucket, objectName) != null) {
            return;
        }
        try {
            StatObjectResponse stat = minioHelper.statObject(imageBucket, objectName);
                saveFileMetadata(imageBucket, objectName, stat.size(), stat.contentType(), inferCategory(objectName),
                    spiderId, title, source);
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
