package com.collect.worker.crawler;

import com.collect.common.mq.TaskMessage;
import com.collect.common.util.ObjectNameUtils;
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

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.TrustManager;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
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

    record WebResource(String url, String category, String objectName, String contentType) {}

    private final SpiderTaskMapper taskMapper;
    private final SpiderTaskLogMapper logMapper;
    private final ElasticsearchOperations elasticsearchOperations;
    private final UrlQueueService urlQueue;
    private final MinioHelper minioHelper;
    private final FileMetadataMapper fileMetadataMapper;
    private final OkHttpClient verifiedHttpClient;
    private final OkHttpClient unverifiedHttpClient;

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
        this.verifiedHttpClient = buildHttpClient(false);
        this.unverifiedHttpClient = buildHttpClient(true);
    }

    static OkHttpClient buildHttpClient(boolean skipTlsVerify) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true);
        if (!skipTlsVerify) {
            return builder.build();
        }

        // This client is selected only for crawlers that explicitly enable skipTlsVerify.
        X509TrustManager trustManager = new X509TrustManager() {
            @Override
            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}

            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}

            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new java.security.cert.X509Certificate[0];
            }
        };

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustManager}, new SecureRandom());
            return builder
                    .sslSocketFactory(sslContext.getSocketFactory(), trustManager)
                    .hostnameVerifier((hostname, session) -> true)
                    .build();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("无法初始化跳过 TLS 证书校验的 HTTP 客户端", e);
        }
    }

    private OkHttpClient httpClient(TaskMessage msg) {
        return Integer.valueOf(1).equals(msg.getSkipTlsVerify())
                ? unverifiedHttpClient
                : verifiedHttpClient;
    }

    @SuppressWarnings("null")
    public void execute(TaskMessage msg) {
        SpiderTask task = taskMapper.selectByTaskId(msg.getTaskId());
        if (task == null) {
            log.warn("任务不存在: {}", msg.getTaskId());
            return;
        }

        Long taskId = msg.getTaskId();
        if (Integer.valueOf(1).equals(msg.getSkipTlsVerify())) {
            log.warn("爬虫已配置跳过 TLS 证书及主机名校验: spiderId={}, taskId={}",
                    msg.getSpiderId(), msg.getTaskId());
        }
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
                    try {
                        f.get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("等待抓取任务执行被中断: taskId={}", taskId, e);
                        break;
                    } catch (java.util.concurrent.ExecutionException e) {
                        fail.incrementAndGet();
                        log.error("抓取任务线程异常: taskId={}", taskId, e.getCause());
                    }
                }
                if (Thread.currentThread().isInterrupted()) break;
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
        taskMapper.setSuccess(task.getId(), success.get());
        taskMapper.setFail(task.getId(), fail.get());
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
                        ? minioHelper.getHtmlIfExists(htmlBucket,
                                "html/" + ObjectNameUtils.base64Url(url) + ".html")
                        : null;
                boolean cacheHit = cachedHtml != null;
                Document doc = cacheHit ? Jsoup.parse(cachedHtml, url) : fetch(url, msg);
                long cost = System.currentTimeMillis() - start;

                ContentParser parsed = ContentParser.parse(doc.outerHtml(), url, msg.getContentSelector());
                String newHtml = doc.outerHtml();
                String newHtmlHash = md5(newHtml);
                boolean overwriteHtml = cacheHit || Integer.valueOf(1).equals(msg.getOverwriteHtml());
                boolean overwriteImage = !cacheHit && Integer.valueOf(1).equals(msg.getOverwriteImage());

                if (!msg.isSingleUrl() && depth < maxDepth) {
                    List<String> next = ContentParser.extractNextUrls(doc, url, maxDepth - depth);
                    next.removeIf(nextUrl -> {
                        String normalized = UrlQueueService.normalizeUrl(nextUrl);
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
                docObj.setImages(List.of());

                saveToElasticsearchWithRetry(docObj);
                writeLog(task.getId(), msg.getSpiderId(), url, 1, "INFO",
                    (overwriteHtml && existingDoc != null) ? "覆盖更新: " + parsed.getTitle() : "抓取成功: " + parsed.getTitle(), (int) cost);
                success.incrementAndGet();

                // Persist the page before downloading static assets so slow asset hosts cannot delay it.
                saveHtmlAndJs(doc, url, newHtml, msg, task, !cacheHit, processedResourceUrls);
                updateImagesAfterPageProcessing(doc, docObj, url, parsed.getTitle(),
                        msg, task, overwriteImage);
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

    private void updateImagesAfterPageProcessing(Document doc, SpiderContentDoc document, String pageUrl,
                                                String title, TaskMessage msg, SpiderTask task,
                                                boolean overwrite) {
        try {
            List<String> imageUrls = shouldSkipImageDownload(doc, msg)
                    ? List.of()
                    : extractAndUploadImages(doc, pageUrl, title, msg, task, overwrite);
            document.setImages(imageUrls);
            saveToElasticsearchWithRetry(document);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("图片结果回写被中断，页面内容已保存: url={}", pageUrl);
        } catch (Exception e) {
            log.warn("图片处理失败，页面内容已保存: url={}", pageUrl, e);
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
            RobotsRules rules = robotsCache.computeIfAbsent(robotsUrl, key -> loadRobotsRules(key, msg));
            return rules.isAllowed(uri.getRawPath());
        } catch (Exception e) {
            log.debug("robots.txt 检查失败，放行 URL: {}", url, e);
            return true;
        }
    }

    private RobotsRules loadRobotsRules(String robotsUrl, TaskMessage msg) {
        try {
            Request request = new Request.Builder()
                    .url(robotsUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CollectX/1.0")
                    .build();
            try (Response response = httpClient(msg).newCall(request).execute()) {
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
        String xpath = msg.getImageXpath();
        if ((xpath == null || xpath.isBlank()) && (selector == null || selector.isBlank())) {
            return List.of();
        }
        List<String> uploadedUrls = new java.util.ArrayList<>();
        long imgStart = System.currentTimeMillis();
        try {
            List<String> imageSources = new java.util.ArrayList<>();
            Elements matched = selector != null && !selector.isBlank()
                    ? doc.select(selector)
                    : new Elements(doc);
            boolean selectorMissed = matched.isEmpty();
            if (selectorMissed) {
                log.info("页面未匹配到图片选择器: url={}, selector={}", pageUrl, selector);
            }
            if (xpath != null && !xpath.isBlank()) {
                if (!selectorMissed) {
                    for (Element scope : matched) {
                        imageSources.addAll(extractImageSourcesByXpath(scope, pageUrl, xpath));
                    }
                }
                if (!selectorMissed && imageSources.isEmpty()) {
                    log.info("页面未匹配到图片 XPath: url={}, selector={}, xpath={}", pageUrl, selector, xpath);
                }
            } else {
                List<Element> imgs = new java.util.ArrayList<>();
                for (Element el : matched) {
                    if ("img".equalsIgnoreCase(el.tagName())) {
                        imgs.add(el);
                    }
                    imgs.addAll(el.select("img"));
                }
                for (Element img : imgs) {
                    String src = img.absUrl("src");
                    if (!src.isBlank()) imageSources.add(src);
                }
            }

            int uploaded = 0;
            int skipped = 0;
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (String src : imageSources) {
                if (src.isBlank() || !seen.add(src)) {
                    continue;
                }
                try {
                    String ext = guessExt(src, null);
                    String objectName = "images/" + ObjectNameUtils.base64Url(src) + ext;
                    // 不覆盖时，若图片已存在则跳过下载
                    if (!overwrite && minioHelper.objectExists(imageBucket, objectName)) {
                        skipped++;
                        saveExistingFileMetadata(objectName, msg.getSpiderId(), title, src);
                        uploadedUrls.add(objectName);
                        continue;
                    }
                    byte[] data = downloadImage(src, msg);
                    if (data == null || data.length == 0) {
                        continue;
                    }
                    // 下载后若扩展名与魔数判断不一致，则用实际扩展名重新命名
                    String realExt = guessExt(src, data);
                    if (!realExt.equals(ext)) {
                        objectName = "images/" + ObjectNameUtils.base64Url(src) + realExt;
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

    static List<String> extractImageSourcesByXpath(Element scope, String pageUrl, String xpath)
            {
        List<String> sources = new java.util.ArrayList<>();
        Document scopedDocument = Jsoup.parse(scope.outerHtml(), pageUrl);
        Elements nodes = scopedDocument.selectXpath(xpath);
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (Element node : nodes) {
            if ("img".equalsIgnoreCase(node.tagName())) {
                addAbsoluteImageSource(seen, pageUrl, node.attr("src"));
            }
            for (Element image : node.select("img")) {
                addAbsoluteImageSource(seen, pageUrl, image.attr("src"));
            }
        }
        sources.addAll(seen);
        return sources;
    }

    private static void addAbsoluteImageSource(java.util.Set<String> sources, String pageUrl, String source) {
        if (source == null || source.isBlank()) return;
        try {
            sources.add(URI.create(pageUrl).resolve(source).toString());
        } catch (IllegalArgumentException ignored) {
            log.debug("图片 XPath 命中的地址无效: {}", source);
        }
    }

    private boolean shouldSkipImageDownload(Document doc, TaskMessage msg) {
        boolean matched = ContentParser.isVipPage(
                doc, msg.getVipSelector(), msg.getVipSelectorContent());
        if (matched) {
            log.info("页面判定为 VIP，跳过图片下载: selector={}, content={}",
                    msg.getVipSelector(), msg.getVipSelectorContent());
        }
        return matched;
    }

    /**
     * 按需上传页面 HTML 原文，并将页面引用的 JS/CSS 文件上传到资源目录。
     * 对象名基于 URL 的 Base64 编码；资源是否覆盖与 HTML 使用同一个覆盖开关。
     */
    private void saveHtmlAndJs(Document doc, String url, String html, TaskMessage msg,
                               SpiderTask task, boolean saveHtml, Set<String> processedResourceUrls) {
        try {
            String urlHash = ObjectNameUtils.base64Url(url);
            if (saveHtml) {
                String htmlObject = "html/" + urlHash + ".html";
                minioHelper.putHtml(htmlBucket, htmlObject, html);
                saveFileMetadata(htmlBucket, htmlObject, html.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                        "text/html; charset=utf-8", "html", msg.getSpiderId(), parsedTitle(doc, url), url);
            }
            Map<String, WebResource> resourceQueue = new java.util.LinkedHashMap<>();
            for (Element script : doc.select("script[src]")) {
                String jsUrl = script.absUrl("src");
                if (jsUrl.isBlank()) {
                    continue;
                }
                String extension = extensionFromUrl(jsUrl);
                String objectName = "js/" + ObjectNameUtils.base64Url(jsUrl)
                        + (extension.isEmpty() ? ".js" : extension);
                enqueueResource(resourceQueue, processedResourceUrls,
                        new WebResource(jsUrl, "js", objectName, "application/javascript"));
            }
            for (Element stylesheet : doc.select("link[href]")) {
                String rel = stylesheet.attr("rel");
                if (java.util.Arrays.stream(rel.split("\\s+"))
                        .noneMatch(value -> "stylesheet".equalsIgnoreCase(value))) {
                    continue;
                }
                String cssUrl = stylesheet.absUrl("href");
                if (cssUrl.isBlank()) {
                    continue;
                }
                String objectName = "css/" + ObjectNameUtils.base64Url(cssUrl) + ".css";
                enqueueResource(resourceQueue, processedResourceUrls,
                        new WebResource(cssUrl, "css", objectName, "text/css"));
            }
            for (WebResource resource : resourceQueue.values()) {
                processWebResource(resource, msg, task, parsedTitle(doc, url));
            }
        } catch (Exception e) {
            log.warn("HTML/JS/CSS 上传失败: url={}", url, e);
        }
    }

    static boolean enqueueResource(Map<String, WebResource> resourceQueue,
                                   Set<String> processedResourceUrls,
                                   WebResource resource) {
        if (!processedResourceUrls.add(resource.url())) {
            return false;
        }
        resourceQueue.put(resource.url(), resource);
        return true;
    }

    private void processWebResource(WebResource resource, TaskMessage msg, SpiderTask task, String title) {
        long start = System.currentTimeMillis();
        try {
            byte[] downloaded = downloadBytes(resource.url(), msg);
            if (downloaded == null || downloaded.length == 0) {
                writeLog(task.getId(), msg.getSpiderId(), resource.url(), 0, "ERROR",
                        resource.category().toUpperCase() + " 下载为空", resourceCost(start), resource.category());
                return;
            }

            byte[] existing = minioHelper.getObjectIfExists(jsBucket, resource.objectName());
            if (existing != null && java.util.Arrays.equals(existing, downloaded)) {
                writeLog(task.getId(), msg.getSpiderId(), resource.url(), 2, "INFO",
                        resource.category().toUpperCase() + " 已存在，内容未变化",
                        resourceCost(start), resource.category());
                return;
            }

            if ("js".equals(resource.category())) {
                minioHelper.putJs(jsBucket, resource.objectName(), downloaded);
            } else {
                minioHelper.putCss(jsBucket, resource.objectName(), downloaded);
            }
            saveOrUpdateResourceMetadata(resource, downloaded.length, msg.getSpiderId(), title);
            String message = existing == null ? "上传成功" : "内容变化，覆盖更新";
            writeLog(task.getId(), msg.getSpiderId(), resource.url(), 1, "INFO",
                    resource.category().toUpperCase() + " " + message,
                    resourceCost(start), resource.category());
        } catch (Exception e) {
            log.warn("{} 下载/上传失败: url={}", resource.category().toUpperCase(), resource.url(), e);
            writeLog(task.getId(), msg.getSpiderId(), resource.url(), 0, "ERROR",
                    resource.category().toUpperCase() + " 下载/上传失败: " + e.getMessage(),
                    resourceCost(start), resource.category());
        }
    }

    private void saveOrUpdateResourceMetadata(WebResource resource, long fileSize, Long spiderId, String title) {
        FileMetadata metadata = fileMetadataMapper.selectByObject(jsBucket, resource.objectName());
        if (metadata == null) {
            saveFileMetadata(jsBucket, resource.objectName(), fileSize, resource.contentType(),
                    resource.category(), spiderId, title, resource.url());
            return;
        }
        metadata.setFileSize(fileSize);
        metadata.setContentType(resource.contentType());
        metadata.setCategory(resource.category());
        metadata.setSpiderId(spiderId);
        metadata.setTitle(title);
        metadata.setSource(resource.url());
        fileMetadataMapper.updateById(metadata);
    }

    private String parsedTitle(Document doc, String url) {
        String title = doc.title();
        return title.isBlank() ? url : title;
    }

    static String extensionFromUrl(String source) {
        String path;
        try {
            path = URI.create(source).getPath();
        } catch (IllegalArgumentException e) {
            return "";
        }
        if (path == null) return "";
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        if (dot <= slash || dot == path.length() - 1) return "";
        return path.substring(dot);
    }

    private int resourceCost(long startTime) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, System.currentTimeMillis() - startTime));
    }

    /**
     * 从 MinIO 读取已存储的 HTML 原文（用于变更比对）。对象不存在或读取失败时返回 null。
     */
    private String readHtmlFromMinio(String url) {
        try {
            return minioHelper.getHtmlIfExists(htmlBucket,
                    "html/" + ObjectNameUtils.base64Url(url) + ".html");
        } catch (Exception e) {
            log.debug("从 MinIO 读取 HTML 失败: url={}", url, e);
            return null;
        }
    }

    private byte[] downloadBytes(String src, TaskMessage msg) throws Exception {
        Request request = new Request.Builder()
                .url(src)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CollectX/1.0")
                .build();
        try (Response response = httpClient(msg).newCall(request).execute()) {
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

    private byte[] downloadImage(String src, TaskMessage msg) throws Exception {
        Request request = new Request.Builder()
                .url(src)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CollectX/1.0")
                .build();
        try (Response response = httpClient(msg).newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            return response.body().bytes();
        }
    }

    static String guessExt(String src, byte[] data) {
        String ext = extensionFromUrl(src);
        String detectedExt = detectImageExtension(data);
        if (!detectedExt.isEmpty()) {
            return detectedExt;
        }
        return ext.isEmpty() ? ".img" : ext;
    }

    private static String detectImageExtension(byte[] data) {
        if (data == null) return "";
        if (data.length >= 4 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return ".png";
        }
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
            return ".jpg";
        }
        if (data.length >= 3 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
            return ".gif";
        }
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return ".webp";
        }
        return "";
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

    private Document fetch(String url, TaskMessage msg) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CollectX/1.0")
                .header("Accept", "text/html,application/xhtml+xml")
                .build();
        try (Response response = httpClient(msg).newCall(request).execute()) {
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
