package com.collect.worker.crawler;

import com.alibaba.fastjson2.JSON;
import com.collect.common.mq.TaskLogMessage;
import com.collect.common.mq.TaskMessage;
import com.collect.common.util.ObjectNameUtils;
import com.collect.worker.es.SpiderContentDoc;
import com.collect.worker.entity.FileMetadata;
import com.collect.worker.entity.Spider;
import com.collect.worker.entity.SpiderTask;
import com.collect.worker.entity.SpiderTaskLog;
import com.collect.worker.mapper.FileMetadataMapper;
import com.collect.worker.mapper.SpiderMapper;
import com.collect.worker.mapper.SpiderTaskLogMapper;
import com.collect.worker.mapper.SpiderTaskMapper;
import com.collect.worker.minio.MinioHelper;
import com.collect.worker.redis.UrlQueueService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import io.minio.StatObjectResponse;
import us.codecraft.webmagic.selector.Html;
import us.codecraft.webmagic.selector.HtmlNode;
import us.codecraft.webmagic.selector.Selectable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.kafka.core.KafkaTemplate;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class CrawlerEngine {

    private static final long TASK_CANCEL_POLL_INTERVAL_MS = 500;

    record WebResource(String url, String category, String objectName, String contentType) {}

    @FunctionalInterface
    private interface ResponseReader<T> {
        T read(Response response) throws Exception;
    }

    static final class TaskExecutionContext {
        private final Set<Call> activeCalls = ConcurrentHashMap.newKeySet();
        private volatile boolean cancelled;

        Call register(Call call) {
            activeCalls.add(call);
            if (cancelled) {
                call.cancel();
            }
            return call;
        }

        void unregister(Call call) {
            activeCalls.remove(call);
        }

        void cancel() {
            cancelled = true;
            activeCalls.forEach(call -> call.cancel());
        }

        boolean isCancelled() {
            return cancelled;
        }
    }

    private final SpiderTaskMapper taskMapper;
    private final SpiderTaskLogMapper logMapper;
    private final SpiderMapper spiderMapper;
    private final ElasticsearchOperations elasticsearchOperations;
    private final UrlQueueService urlQueue;
    private final MinioHelper minioHelper;
    private final FileMetadataMapper fileMetadataMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OkHttpClient verifiedHttpClient;
    private final OkHttpClient unverifiedHttpClient;

    @Value("${app.es.content-index:spider_content}")
    private String contentIndex;

    @Value("${app.kafka.task-log-topic:spider_task_log_topic-default}")
    private String taskLogTopic;

    @Value("${minio.image-bucket:crawler}")
    private String imageBucket;

    @Value("${minio.html-bucket:crawler}")
    private String htmlBucket;

    @Value("${minio.js-bucket:crawler}")
    private String jsBucket;

    public CrawlerEngine(SpiderTaskMapper taskMapper, SpiderTaskLogMapper logMapper,
                         SpiderMapper spiderMapper,
                         ElasticsearchOperations elasticsearchOperations, UrlQueueService urlQueue,
                         MinioHelper minioHelper, FileMetadataMapper fileMetadataMapper,
                         KafkaTemplate<String, String> kafkaTemplate) {
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
        this.spiderMapper = spiderMapper;
        this.elasticsearchOperations = elasticsearchOperations;
        this.urlQueue = urlQueue;
        this.minioHelper = minioHelper;
        this.fileMetadataMapper = fileMetadataMapper;
        this.kafkaTemplate = kafkaTemplate;
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
        int concurrency = msg.getUrlConcurrency() != null ? msg.getUrlConcurrency() : 8;
        if (concurrency < 1) concurrency = 1;
        if (concurrency > 50) concurrency = 50;
        ExecutorService executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        TaskExecutionContext execution = new TaskExecutionContext();
        Map<String, RobotsRules> robotsCache = new ConcurrentHashMap<>();

        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger fail = new AtomicInteger(0);
        Set<String> processedResourceUrls = ConcurrentHashMap.newKeySet();
        // 读取缓存模式下缓存命中的页面URL（任务结束后合并回爬虫配置的起始URL）
        Set<String> cachedStartUrls = ConcurrentHashMap.newKeySet();
        Set<Future<?>> activeFutures = ConcurrentHashMap.newKeySet();
        boolean executorTerminated = true;

        try {
            for (String startUrl : msg.getStartUrls()) {
                String normalizedStartUrl = UrlQueueService.normalizeUrl(startUrl);
                if (normalizedStartUrl == null || normalizedStartUrl.isBlank()) continue;
                urlQueue.enqueueIfAbsent(taskId, normalizedStartUrl, 0);
            }
            // 读取缓存模式下，起始URL也优先从缓存发现：MinIO 中与已配置起始URL同域名的HTML对象
            if (Integer.valueOf(1).equals(msg.getReadCache())) {
                enqueueCachedUrlsByDomain(taskId, msg.getStartUrls());
            }

            while (true) {
                // 任务状态不是运行中（如被取消）时停止爬取
                SpiderTask latest = taskMapper.selectById(task.getId());
                if (latest == null || !"RUNNING".equals(latest.getStatus())) {
                    log.info("任务状态不是运行中，停止爬取: taskId={}, status={}", taskId, latest == null ? null : latest.getStatus());
                    break;
                }
                // 任务被暂停时等待，直到恢复或取消（不退出循环，恢复后继续爬取剩余队列）
                if (latest.getPausedAt() != null) {
                    awaitAnyFuture(activeFutures, TASK_CANCEL_POLL_INTERVAL_MS);
                    SpiderTask check = taskMapper.selectById(task.getId());
                    if (check == null || !"RUNNING".equals(check.getStatus())) {
                        execution.cancel();
                        break;
                    }
                    continue;
                }
                // 清理已完成的 future
                activeFutures.removeIf(f -> f.isDone());
                // 队列为空且没有活跃任务 → 全部完成（暂停中的任务由上面的等待逻辑处理，不会到这里退出）
                if (activeFutures.isEmpty() && urlQueue.size(taskId) == 0) break;
                // 达到并发上限，等待至少一个任务完成
                if (activeFutures.size() >= concurrency) {
                    awaitAnyFuture(activeFutures, TASK_CANCEL_POLL_INTERVAL_MS);
                    SpiderTask check = taskMapper.selectById(task.getId());
                    if (check == null || !"RUNNING".equals(check.getStatus())) {
                        execution.cancel();
                        break;
                    }
                    continue;
                }
                // 尝试从队列取一个 URL
                String item = urlQueue.pop(taskId);
                if (item == null) {
                    if (activeFutures.isEmpty()) break;
                    // 队列为空但有活跃任务，等待一个完成后再检查新入队的 URL
                    awaitAnyFuture(activeFutures, TASK_CANCEL_POLL_INTERVAL_MS);
                    SpiderTask check = taskMapper.selectById(task.getId());
                    if (check == null || !"RUNNING".equals(check.getStatus())) {
                        execution.cancel();
                        break;
                    }
                    continue;
                }
                String[] parts = item.split("\t", 2);
                String url = parts[0];
                if (!urlQueue.claimForProcessing(taskId, url)) {
                    continue;
                }
                int depth = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                final String crawlUrl = url;
                final int crawlDepth = depth;
                Future<?> future = executor.submit(() ->
                        crawlUrl(crawlUrl, crawlDepth, maxDepth, msg, task, taskId, success, fail,
                                robotsCache, processedResourceUrls, cachedStartUrls, execution)
                );
                activeFutures.add(future);
            }
        } finally {
            if (execution.isCancelled()) {
                executor.shutdownNow();
            } else {
                executor.shutdown();
            }
            if (!awaitExecutorTermination(executor, 30, TimeUnit.SECONDS)) {
                executorTerminated = false;
                execution.cancel();
                executor.shutdownNow();
                awaitExecutorTermination(executor, Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            }
        }

        LocalDateTime endTime = LocalDateTime.now();
        SpiderTask latestTask = taskMapper.selectById(task.getId());
        boolean isPaused = latestTask != null
                && "RUNNING".equals(latestTask.getStatus())
                && latestTask.getPausedAt() != null;
        if (isPaused) {
            // 暂停时不清空队列，保留剩余URL供恢复后继续爬取
            log.info("任务处于暂停状态，保留队列和进度: taskId={}, queueSize={}", taskId, urlQueue.size(taskId));
            taskMapper.setSuccess(task.getId(), success.get());
            taskMapper.setFail(task.getId(), fail.get());
            return;
        }
        // 非暂停状态，清空队列
        urlQueue.clear(taskId);
        // 读取缓存模式：任务结束后，把本次缓存命中的页面URL合并回爬虫配置的起始URL，
        // 使下次运行仍从缓存读取（缓存发现以配置的起始URL为基准，配置变化后不会复活旧缓存URL）
        if (Integer.valueOf(1).equals(msg.getReadCache())) {
            mergeCachedStartUrls(msg.getSpiderId(), cachedStartUrls);
        }

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

    private boolean awaitAnyFuture(Set<Future<?>> futures, long timeoutMs) {
        if (futures.isEmpty()) return true;
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            for (Future<?> f : futures) {
                if (f.isDone()) return true;
            }
            try {
                Thread.sleep(Math.min(100, deadline - System.currentTimeMillis()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return true;
            }
        }
        return false;
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
                           Map<String, RobotsRules> robotsCache,
                           Set<String> processedResourceUrls,
                           Set<String> cachedStartUrls,
                           TaskExecutionContext execution) {
        try {
            // 任务状态不是运行中（如被取消）时停止爬取
            SpiderTask latest = taskMapper.selectById(task.getId());
            if (latest == null || !"RUNNING".equals(latest.getStatus())) {
                log.info("任务状态不是运行中，停止爬取: taskId={}, url={}", taskId, url);
                return;
            }
            if (!isAllowedByRobots(url, msg, robotsCache, execution)) {
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
            if (cacheHit) {
                cachedStartUrls.add(url);
            }
            if (readCache && !cacheHit) {
                // 读取缓存模式：所有内容只从 MinIO 获取，未缓存的页面不联网
                writeLog(task.getId(), msg.getSpiderId(), url, 2, "INFO",
                        "缓存未命中，跳过（读取缓存模式不联网）", 0);
                log.info("读取缓存模式，缓存未命中，跳过: url={}", url);
                success.incrementAndGet();
                return;
            }
            Html doc = cacheHit ? new Html(cachedHtml, url) : fetch(url, msg, execution);
            long cost = System.currentTimeMillis() - start;

            ContentParser parsed = ContentParser.parse(doc.get(), url, msg.getContentSelector());
            String newHtml = doc.get();
            String newHtmlHash = md5(newHtml);
            boolean overwriteHtml = cacheHit || Integer.valueOf(1).equals(msg.getOverwriteHtml());
            boolean overwriteImage = !cacheHit && Integer.valueOf(1).equals(msg.getOverwriteImage());

            // 读取缓存模式不联网：起始URL范围已按 MinIO 缓存发现，链接扩展只会产生
            // 未缓存的URL，入队后也会被跳过，因此不再扩展
            if (!msg.isSingleUrl() && depth < maxDepth && !readCache) {
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
                    if (!isAllowedByRobots(normalizedNextUrl, msg, robotsCache, execution)) continue;
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
            // 已存在内容覆盖时：抓取时间保留首次抓取时间，更新时间设为当前时间
            boolean isOverwrite = existingDoc != null;
            String crawlTime = isOverwrite && existingDoc.getCrawlTime() != null
                    ? existingDoc.getCrawlTime()
                    : LocalDateTime.now().format(esDateFormatter);
            docObj.setCrawlTime(crawlTime);
            docObj.setUpdateTime(LocalDateTime.now().format(esDateFormatter));
            docObj.setImages(List.of());

            saveToElasticsearchWithRetry(docObj);
            writeLog(task.getId(), msg.getSpiderId(), url, 1, "INFO",
                (overwriteHtml && existingDoc != null) ? "覆盖更新: " + parsed.getTitle() : "抓取成功: " + parsed.getTitle(), (int) cost);
            success.incrementAndGet();

            // Persist the page before downloading static assets so slow asset hosts cannot delay it.
            // 读取缓存命中时，HTML 和 JS/CSS 资源都直接从 MinIO 复用，不再回源。
            boolean saveResources = !cacheHit || Integer.valueOf(1).equals(msg.getOverwriteHtml());
            saveHtmlAndJs(doc, url, newHtml, msg, task, !cacheHit, saveResources, processedResourceUrls, execution);
            updateImagesAfterPageProcessing(doc, docObj, url, parsed.getTitle(),
                    msg, task, overwriteImage, execution);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!execution.isCancelled()) {
                fail.incrementAndGet();
                writeLog(task.getId(), msg.getSpiderId(), url, 0, "ERROR", "任务线程被中断", 0);
            }
        } catch (Exception e) {
            if (execution.isCancelled()) {
                return;
            }
            fail.incrementAndGet();
            log.warn("抓取失败: {}", url, e);
            writeLog(task.getId(), msg.getSpiderId(), url, 0, "ERROR", e.getMessage(), 0);
        }
    }

    private void updateImagesAfterPageProcessing(Html doc, SpiderContentDoc document, String pageUrl,
                                                String title, TaskMessage msg, SpiderTask task,
                                                boolean overwrite, TaskExecutionContext execution) {
        try {
            List<String> imageUrls = shouldSkipImageDownload(doc, msg)
                    ? List.of()
                    : extractAndUploadImages(doc, pageUrl, title, msg, task, overwrite, execution);
            document.setImages(imageUrls);
            saveToElasticsearchWithRetry(document);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!execution.isCancelled()) {
                log.warn("图片结果回写被中断，页面内容已保存: url={}", pageUrl);
            }
        } catch (Exception e) {
            if (!execution.isCancelled()) {
                log.warn("图片处理失败，页面内容已保存: url={}", pageUrl, e);
            }
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

    /**
     * 读取缓存模式：从 MinIO 的 html/ 目录发现已缓存的页面URL并入队。
     * 只收录与已配置起始URL同域名的缓存（避免跨域污染）。
     * 发现失败时记录日志，不影响任务。
     */
    private void enqueueCachedUrlsByDomain(Long taskId, List<String> startUrls) {
        Set<String> hosts = new java.util.HashSet<>();
        for (String startUrl : startUrls) {
            try {
                URI uri = URI.create(startUrl);
                String host = uri.getHost();
                if (host != null && !host.isBlank()) {
                    hosts.add(host.toLowerCase(java.util.Locale.ROOT));
                }
            } catch (Exception e) {
                log.debug("起始URL域名解析失败: {}", startUrl);
            }
        }
        if (hosts.isEmpty()) {
            return;
        }
        try {
            for (String objectName : minioHelper.listObjectNamesByPrefixAndSuffix(htmlBucket, "html/", ".html")) {
                String base64Part = objectName.substring("html/".length(), objectName.length() - ".html".length());
                String url;
                try {
                    url = new String(java.util.Base64.getUrlDecoder().decode(base64Part),
                            java.nio.charset.StandardCharsets.UTF_8);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                String host = extractHost(url);
                if (host == null || !hosts.contains(host)) {
                    continue;
                }
                String normalized = UrlQueueService.normalizeUrl(url);
                if (normalized != null && !normalized.isBlank()) {
                    urlQueue.enqueueIfAbsent(taskId, normalized, 0);
                }
            }
        } catch (Exception e) {
            log.warn("从缓存发现起始URL失败: spider hosts={}", hosts, e);
        }
    }

    /**
     * 任务结束后把缓存命中的URL合并进爬虫配置的起始URL（只增不删，保留人工配置）。
     */
    private void mergeCachedStartUrls(Long spiderId, Set<String> cachedUrls) {
        if (cachedUrls == null || cachedUrls.isEmpty()) {
            return;
        }
        try {
            Spider spider = spiderMapper.selectById(spiderId);
            if (spider == null) {
                return;
            }
            List<String> existing;
            try {
                existing = com.alibaba.fastjson2.JSON.parseArray(spider.getStartUrls(), String.class);
            } catch (Exception e) {
                existing = new java.util.ArrayList<>();
            }
            if (existing == null) {
                existing = new java.util.ArrayList<>();
            }
            Set<String> existingNormalized = new java.util.HashSet<>();
            for (String u : existing) {
                String n = UrlQueueService.normalizeUrl(u);
                if (n != null) {
                    existingNormalized.add(n);
                }
            }
            List<String> merged = new java.util.ArrayList<>(existing);
            int added = 0;
            for (String u : cachedUrls) {
                String n = UrlQueueService.normalizeUrl(u);
                if (n != null && !existingNormalized.add(n)) {
                    continue;
                }
                merged.add(u);
                added++;
            }
            if (added == 0) {
                return;
            }
            Spider update = new Spider();
            update.setId(spider.getId());
            update.setStartUrls(com.alibaba.fastjson2.JSON.toJSONString(merged));
            spiderMapper.updateById(update);
            log.info("已将缓存发现的URL合并回爬虫起始URL: spiderId={}, added={}", spiderId, added);
        } catch (Exception e) {
            log.warn("合并缓存URL回爬虫起始URL失败: spiderId={}", spiderId, e);
        }
    }

    private String extractHost(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? null : host.toLowerCase(java.util.Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeHost(String host) {
        if (host == null || host.isBlank()) return null;
        String normalized = host.toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("www.") ? normalized.substring(4) : normalized;
    }

    private boolean isAllowedByRobots(String url, TaskMessage msg, Map<String, RobotsRules> robotsCache,
                                     TaskExecutionContext execution) {
        // 读取缓存模式不联网：不请求 robots.txt
        if (Integer.valueOf(1).equals(msg.getReadCache())) return true;
        if (!Integer.valueOf(1).equals(msg.getFollowRobots())) return true;
        try {
            URI uri = URI.create(url);
            if (uri.getHost() == null) return false;
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
            String robotsUrl = scheme + "://" + uri.getAuthority() + "/robots.txt";
            RobotsRules rules = robotsCache.computeIfAbsent(robotsUrl, key -> loadRobotsRules(key, msg, execution));
            return rules.isAllowed(uri.getRawPath());
        } catch (Exception e) {
            log.debug("robots.txt 检查失败，放行 URL: {}", url, e);
            return true;
        }
    }

    private RobotsRules loadRobotsRules(String robotsUrl, TaskMessage msg, TaskExecutionContext execution) {
        try {
            Request request = new Request.Builder()
                    .url(robotsUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CollectX/1.0")
                    .build();
            return executeRequest(httpClient(msg), request, execution, response -> {
                if (!response.isSuccessful() || response.body() == null) return RobotsRules.allowAll();
                return RobotsRules.parse(response.body().string());
            });
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
     * 提取页面图片并上传到 MinIO。优先使用图片 CSS 选择器定位范围：
     * - 仅配置选择器：取选择器命中元素自身及内部的 <img>；
     * - 选择器 + XPath：在选择器命中的元素范围内再按 XPath 定位图片；
     * - 仅配置 XPath：直接在整页范围内按 XPath 定位图片。
     * overwrite=true 时重新下载并覆盖已存在的图片；否则跳过已存在的图片。
     * 返回已上传图片的 objectName 列表（无图片时返回空列表）。
     */
    private List<String> extractAndUploadImages(Html doc, String pageUrl, String title,
                                                TaskMessage msg, SpiderTask task, boolean overwrite,
                                                TaskExecutionContext execution) {
        if (execution.isCancelled()) return List.of();
        String selector = msg.getImageSelector();
        String xpath = msg.getImageXpath();
        boolean hasSelector = selector != null && !selector.isBlank();
        boolean hasXpath = xpath != null && !xpath.isBlank();
        if (!hasSelector && !hasXpath) {
            return List.of();
        }
        List<String> uploadedUrls = new java.util.ArrayList<>();
        long imgStart = System.currentTimeMillis();
        try {
            List<String> imageSources = extractImageSources(doc, pageUrl, selector, xpath);
            if (imageSources.isEmpty()) {
                log.info("页面未匹配到图片: url={}, selector={}, xpath={}", pageUrl, selector, xpath);
            }

            int uploaded = 0;
            int skipped = 0;
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (String src : imageSources) {
                if (execution.isCancelled()) break;
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
                    byte[] data = downloadImage(src, msg, execution);
                    if (execution.isCancelled() || data == null || data.length == 0) {
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
                    if (execution.isCancelled()) break;
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
            if (execution.isCancelled()) return uploadedUrls;
            log.warn("图片提取失败: url={}", pageUrl, e);
        }
        return uploadedUrls;
    }

    static List<String> extractImageSources(Html doc, String pageUrl, String selector, String xpath) {
        if (doc == null) {
            return List.of();
        }
        boolean hasSelector = selector != null && !selector.isBlank();
        List<String> xpaths = parseImageXpaths(xpath);
        boolean hasXpath = !xpaths.isEmpty();
        if (!hasSelector && !hasXpath) {
            return List.of();
        }

        java.util.Set<String> sources = new java.util.LinkedHashSet<>();
        if (hasSelector) {
            List<Selectable> matched = doc.$(selector).nodes();
            if (matched.isEmpty()) {
                return List.of();
            }
            if (hasXpath) {
                // 选择器 + XPath：在选择器命中的元素范围内按 XPath 定位图片
                for (Selectable scope : matched) {
                    for (String expression : xpaths) {
                        sources.addAll(extractImageSourcesByXpath(scope, pageUrl, expression));
                    }
                }
                return new java.util.ArrayList<>(sources);
            }
            // 仅选择器：取命中元素自身及内部的 <img>
            for (Selectable scope : matched) {
                for (String source : scope.xpath("//img/@src").all()) {
                    addAbsoluteImageSource(sources, pageUrl, source);
                }
            }
            return new java.util.ArrayList<>(sources);
        }

        // 仅 XPath：直接在整页范围内按 XPath 定位图片
        for (String expression : xpaths) {
            sources.addAll(extractImageSourcesByXpath(doc, pageUrl, expression));
        }
        return new java.util.ArrayList<>(sources);
    }

    private static List<String> parseImageXpaths(String xpath) {
        if (xpath == null || xpath.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(xpath.split(";"))
                .map(String::trim)
                .filter(expression -> !expression.isEmpty())
                .toList();
    }

    static List<String> extractImageSourcesByXpath(Selectable scope, String pageUrl, String xpath) {
        List<String> sources = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        Selectable matched = scope.xpath(xpath);
        if (matched instanceof HtmlNode) {
            for (Selectable node : matched.nodes()) {
                List<String> imageAttributes = node.xpath("//img/@src").all();
                for (String source : imageAttributes) {
                    addAbsoluteImageSource(seen, pageUrl, source);
                }
            }
        } else {
            for (String source : matched.all()) {
                addAbsoluteImageSource(seen, pageUrl, source);
            }
        }
        sources.addAll(seen);
        return sources;
    }

    private static void addAbsoluteImageSource(java.util.Set<String> sources, String pageUrl, String source) {
        if (source == null || source.isBlank()) return;
        try {
            URI resolved = URI.create(pageUrl).resolve(source.trim());
            String scheme = resolved.getScheme();
            if (resolved.getHost() == null || scheme == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                log.debug("图片 XPath 命中的地址无效: {}", source);
                return;
            }
            sources.add(resolved.toString());
        } catch (IllegalArgumentException ignored) {
            log.debug("图片 XPath 命中的地址无效: {}", source);
        }
    }

    private boolean shouldSkipImageDownload(Html doc, TaskMessage msg) {
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
     * saveResources=false 时（读取缓存命中且未开启覆盖HTML）只处理 HTML，
     * 页面引用的 JS/CSS 全部直接从 MinIO 复用，不回源。
     */
    private void saveHtmlAndJs(Html doc, String url, String html, TaskMessage msg,
                               SpiderTask task, boolean saveHtml, boolean saveResources,
                               Set<String> processedResourceUrls,
                               TaskExecutionContext execution) {
        if (execution.isCancelled()) return;
        try {
            String urlHash = ObjectNameUtils.base64Url(url);
            if (saveHtml) {
                String htmlObject = "html/" + urlHash + ".html";
                minioHelper.putHtml(htmlBucket, htmlObject, html);
                saveFileMetadata(htmlBucket, htmlObject, html.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                        "text/html; charset=utf-8", "html", msg.getSpiderId(), parsedTitle(doc, url), url);
            }
            if (!saveResources) {
                return;
            }
            Map<String, WebResource> resourceQueue = new java.util.LinkedHashMap<>();
            for (String source : doc.$("script[src]", "src").all()) {
                String jsUrl = resolveResourceUrl(url, source);
                if (jsUrl == null) {
                    continue;
                }
                String extension = extensionFromUrl(jsUrl);
                String objectName = "js/" + ObjectNameUtils.base64Url(jsUrl)
                        + (extension.isEmpty() ? ".js" : extension);
                enqueueResource(resourceQueue, processedResourceUrls,
                        new WebResource(jsUrl, "js", objectName, "application/javascript"));
            }
            for (String source : doc.$("link[rel~=stylesheet][href]", "href").all()) {
                String cssUrl = resolveResourceUrl(url, source);
                if (cssUrl == null) {
                    continue;
                }
                String objectName = "css/" + ObjectNameUtils.base64Url(cssUrl) + ".css";
                enqueueResource(resourceQueue, processedResourceUrls,
                        new WebResource(cssUrl, "css", objectName, "text/css"));
            }
            for (WebResource resource : resourceQueue.values()) {
                if (execution.isCancelled()) break;
                processWebResource(resource, msg, task, parsedTitle(doc, url), execution);
            }
        } catch (Exception e) {
            if (execution.isCancelled()) return;
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

    private void processWebResource(WebResource resource, TaskMessage msg, SpiderTask task, String title,
                                    TaskExecutionContext execution) {
        long start = System.currentTimeMillis();
        try {
            byte[] downloaded = downloadBytes(resource.url(), msg, execution);
            if (execution.isCancelled()) return;
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
            if (execution.isCancelled()) return;
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

    private String parsedTitle(Html doc, String url) {
        List<Selectable> titleElements = doc.$("title").nodes();
        String title = titleElements.isEmpty() ? "" : ContentParser.textOf(titleElements.get(0));
        return title.isBlank() ? url : title;
    }

    private String resolveResourceUrl(String pageUrl, String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        try {
            URI resolved = URI.create(pageUrl).resolve(source.trim());
            String scheme = resolved.getScheme();
            if (resolved.getHost() == null || scheme == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                return null;
            }
            return resolved.toString();
        } catch (IllegalArgumentException e) {
            log.debug("页面资源地址无效: pageUrl={}, source={}", pageUrl, source);
            return null;
        }
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

    private byte[] downloadBytes(String src, TaskMessage msg, TaskExecutionContext execution) throws Exception {
        Request request = new Request.Builder()
                .url(src)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CollectX/1.0")
                .build();
        return executeRequest(httpClient(msg), request, execution, response -> {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            return response.body().bytes();
        });
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

    private byte[] downloadImage(String src, TaskMessage msg, TaskExecutionContext execution) throws Exception {
        Request request = new Request.Builder()
                .url(src)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CollectX/1.0")
                .build();
        return executeRequest(httpClient(msg), request, execution, response -> {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            return response.body().bytes();
        });
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

    private Html fetch(String url, TaskMessage msg, TaskExecutionContext execution) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CollectX/1.0")
                .header("Accept", "text/html,application/xhtml+xml")
                .build();
        return executeRequest(httpClient(msg), request, execution, response -> {
            if (!response.isSuccessful()) {
                throw new RuntimeException("HTTP " + response.code());
            }
            if (response.body() == null) {
                throw new IllegalStateException("HTTP 响应正文为空");
            }
            return new Html(response.body().string(), url);
        });
    }

    private <T> T executeRequest(OkHttpClient client, Request request,
                                 TaskExecutionContext execution, ResponseReader<T> reader) throws Exception {
        Call call = execution.register(client.newCall(request));
        try (Response response = call.execute()) {
            return reader.read(response);
        } finally {
            execution.unregister(call);
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
        TaskLogMessage logMsg = new TaskLogMessage();
        logMsg.setTaskId(taskId);
        logMsg.setSpiderId(spiderId);
        logMsg.setUrl(url);
        logMsg.setStatus(status);
        logMsg.setLevel(level);
        logMsg.setType(type);
        logMsg.setMessage(message != null && message.length() > 500 ? message.substring(0, 500) : message);
        logMsg.setCostMs(costMs);
        logMsg.setCreateTime(LocalDateTime.now());
        try {
            kafkaTemplate.send(taskLogTopic, JSON.toJSONString(logMsg));
        } catch (Exception e) {
            // Kafka 发送失败时降级为直接写库，保证日志不丢
            log.warn("任务日志发送 Kafka 失败，降级直接写库: taskId={}, url={}", taskId, url, e);
            SpiderTaskLog logEntry = new SpiderTaskLog();
            logEntry.setTaskId(taskId);
            logEntry.setSpiderId(spiderId);
            logEntry.setUrl(url);
            logEntry.setStatus(status);
            logEntry.setLevel(level);
            logEntry.setType(type);
            logEntry.setMessage(logMsg.getMessage());
            logEntry.setCostMs(costMs);
            logMapper.insert(logEntry);
        }
    }
}
