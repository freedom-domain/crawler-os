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
import okhttp3.ConnectionPool;
import okhttp3.Dns;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
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
import java.net.InetAddress;
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
    /** 空闲连接保留时长（分钟）；<=0 表示禁用连接池 */
    @Value("${app.http.keep-alive-minutes:2}")
    private int httpKeepAliveMinutes;
    /** 空闲连接上限；0 表示禁用连接池，-1 表示使用 OkHttp 默认值(5) */
    @Value("${app.http.max-idle-connections:512}")
    private int httpMaxIdleConnections;
    /** 全部客户端合计的最大并发请求数（含排队）；<=0 表示使用 OkHttp 默认值(64) */
    @Value("${app.http.max-concurrent-requests:2000}")
    private int httpMaxConcurrentRequests;
    /** 单客户端的最大并发请求数；<=0 表示使用 OkHttp 默认值(5) */
    @Value("${app.http.max-concurrent-requests-per-client:200}")
    private int httpMaxConcurrentRequestsPerClient;
    /** 连接建立超时（秒） */
    @Value("${app.http.connect-timeout-seconds:15}")
    private int httpConnectTimeoutSeconds;
    /** 读取超时（秒） */
    @Value("${app.http.read-timeout-seconds:30}")
    private int httpReadTimeoutSeconds;
    /** 带自定义 Header 的客户端缓存上限 */
    @Value("${app.http.max-cached-clients:128}")
    private int httpMaxCachedClients;
    /** 响应体大小上限（字节）；0=不限制。超限截断并丢弃，防止大页面撑爆堆内存 */
    @Value("${app.http.max-response-bytes:10485760}")
    private long httpMaxResponseBytes;
    /** 瞬时网络错误（IOException）重试次数，仅对幂等请求生效；0=不重试 */
    @Value("${app.http.retry-on-io-error:1}")
    private int httpRetryOnIoError;
    /** 本地 DNS 缓存 TTL（秒）；0=禁用 DNS 缓存。爬取多域名时避免重复 DNS 查询 */
    @Value("${app.http.dns-cache-ttl-seconds:300}")
    private long httpDnsCacheTtlSeconds;
    /** 连接池监控日志间隔（秒）；0=关闭 */
    @Value("${app.http.pool-metrics-log-seconds:60}")
    private int httpPoolMetricsLogSeconds;
    /** 单任务最大并发请求数（任务级 Dispatcher 上限），防止一个任务独占全局并发槽位 */
    @Value("${app.http.max-concurrent-requests-per-task:100}")
    private int httpMaxConcurrentRequestsPerTask;
    /** 单任务每主机最大并发请求数 */
    @Value("${app.http.max-concurrent-requests-per-task-per-host:50}")
    private int httpMaxConcurrentRequestsPerTaskPerHost;

    /** 所有客户端共享的连接池：不同 header/TLS 组合的客户端复用同一条 TCP 连接 */
    private ConnectionPool sharedConnectionPool;
    /** 进程级 robots 缓存（按 authority），跨任务复用，TTL 30 分钟 */
    private final Map<String, RobotsCacheEntry> robotsCacheByHost = new ConcurrentHashMap<>();
    /** 本地 DNS 缓存（name -> (addresses, expireAt)） */
    private final Map<String, DnsCacheEntry> dnsCache = new ConcurrentHashMap<>();
    /** 任务级客户端（线程本地）：每个爬取虚拟线程绑定自己任务的 Dispatcher，实现调度隔离 */
    private final ThreadLocal<OkHttpClient> currentTaskClient = new ThreadLocal<>();
    /** 任务状态本地缓存（taskId -> (status, pausedAt, expireAt)），TTL 1s，减少 DB 查询 */
    private final Map<Long, TaskStatusCache> taskStatusCache = new ConcurrentHashMap<>();

    record WebResource(String url, String category, String objectName, String contentType) {}

    record RobotsCacheEntry(RobotsRules rules, long expireAt) {}

    record DnsCacheEntry(java.util.List<InetAddress> addresses, long expireAt) {}

    /** 任务状态缓存条目：status + pausedAt + 过期时间 */
    record TaskStatusCache(String status, java.time.LocalDateTime pausedAt, long expireAt) {}

    /**
     * 连接池/超时配置，取自 {@code app.http.*}；各字段 <=0 表示禁用/沿用 OkHttp 默认值。
     */
    record PoolSettings(int connectTimeoutSeconds,
                        int readTimeoutSeconds,
                        int maxIdleConnections,
                        int keepAliveMinutes,
                        int maxConcurrentRequests,
                        int maxConcurrentRequestsPerClient) {
        /** 测试/无配置场景下的默认值 */
        static PoolSettings defaults() {
            return new PoolSettings(15, 30, 512, 2, 2000, 200);
        }
    }

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
    private final java.util.Map<String, OkHttpClient> httpClientCache = new ConcurrentHashMap<>();
    private final java.util.Map<String, List<String>> excludedUrlsCache = new ConcurrentHashMap<>();

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
        this.sharedConnectionPool = new ConnectionPool(
                Math.max(0, httpMaxIdleConnections),
                Math.max(1, httpKeepAliveMinutes), TimeUnit.MINUTES);
        this.verifiedHttpClient = buildClientWithDnsAndRetry(
                buildHttpClient(false, null, poolSettings(), sharedConnectionPool));
        this.unverifiedHttpClient = buildClientWithDnsAndRetry(
                buildHttpClient(true, null, poolSettings(), sharedConnectionPool));
        // 客户端缓存上限：自定义 Header 的客户端按 (skipTls, headers) 无限缓存，
        // 任务量增长后旧 Header 组合的客户端及其连接池会一直留在内存里，这里做上限保护
        if (httpClientCache.size() > Math.max(1, httpMaxCachedClients)) {
            log.warn("HTTP 客户端缓存达到上限，清空重建（连接池随之回收）: size={}", httpClientCache.size());
            httpClientCache.clear();
        }
    }

    private PoolSettings poolSettings() {
        return new PoolSettings(httpConnectTimeoutSeconds, httpReadTimeoutSeconds,
                httpMaxIdleConnections, httpKeepAliveMinutes,
                httpMaxConcurrentRequests, httpMaxConcurrentRequestsPerClient);
    }

    /**
     * 给客户端叠加：本地 DNS 缓存（{@code app.http.dns-cache-ttl-seconds} > 0 时）
     * 和瞬时网络错误重试（{@code app.http.retry-on-io-error} > 0 时，仅幂等请求）。
     * 基于 {@link OkHttpClient#newBuilder()} 派生，连接池/Dispatcher/超时/TLS 均继承自原客户端。
     */
    private OkHttpClient buildClientWithDnsAndRetry(OkHttpClient base) {
        OkHttpClient.Builder builder = base.newBuilder();
        if (httpDnsCacheTtlSeconds > 0) {
            builder.dns(dnsWithCache());
        }
        if (httpRetryOnIoError > 0) {
            builder.addInterceptor(retryOnIoErrorInterceptor());
        }
        return builder.build();
    }

    /**
     * 为单个任务创建带独立 Dispatcher 的客户端（方案 A：连接池共享 + 调度隔离）。
     *
     * <p>每个任务一个 {@link Dispatcher}，单任务并发上限
     * {@code app.http.max-concurrent-requests-per-task}（默认 100），
     * 单任务每主机上限 {@code app.http.max-concurrent-requests-per-task-per-host}（默认 50）。
     * 连接池、DNS 缓存、重试 Interceptor、TLS 配置均继承自共享客户端，
     * 仅 Dispatcher 按任务隔离——防止一个任务的慢请求独占全局并发槽位。
     *
     * <p>任务结束时调用 {@code client.dispatcher().executorService().shutdown()} 释放线程。
     * 同一任务的多个虚拟线程共享同一个 taskClient（通过 {@link #taskClientFor} 缓存）。
     */
    private OkHttpClient taskClientFor(TaskMessage msg) {
        boolean skipTls = Integer.valueOf(1).equals(msg.getSkipTlsVerify());
        String headers = msg.getHeaders();
        // 选择基础客户端（含 header Interceptor + DNS + 重试 + 共享连接池）
        OkHttpClient base = (headers == null || headers.isBlank())
                ? (skipTls ? unverifiedHttpClient : verifiedHttpClient)
                : httpClientCache.computeIfAbsent(
                        (skipTls ? "unverified" : "verified") + "|" + headers,
                        k -> buildClientWithDnsAndRetry(
                                buildHttpClient(skipTls, headers, poolSettings(), sharedConnectionPool)));
        // 派生任务级客户端：独立 Dispatcher，其余全部继承
        Dispatcher taskDispatcher = new Dispatcher();
        taskDispatcher.setMaxRequests(Math.max(1, httpMaxConcurrentRequestsPerTask));
        taskDispatcher.setMaxRequestsPerHost(Math.max(1, httpMaxConcurrentRequestsPerTaskPerHost));
        return base.newBuilder().dispatcher(taskDispatcher).build();
    }

    /** 带本地 TTL 缓存的 DNS 解析器；缓存条目按 host 去重，过期自动清除 */
    private Dns dnsWithCache() {
        return hostname -> {
            long now = System.currentTimeMillis();
            DnsCacheEntry entry = dnsCache.get(hostname);
            if (entry != null && entry.expireAt() > now) {
                return entry.addresses();
            }
            java.util.List<InetAddress> addresses = Dns.SYSTEM.lookup(hostname);
            if (addresses != null && !addresses.isEmpty()) {
                dnsCache.put(hostname, new DnsCacheEntry(addresses,
                        now + httpDnsCacheTtlSeconds * 1000L));
            }
            // 防止缓存无限增长：超出 1024 条时清空（简单粗暴但安全）
            if (dnsCache.size() > 1024) {
                dnsCache.clear();
            }
            return addresses;
        };
    }

    /**
     * 瞬时网络错误重试：{@link java.io.IOException}（连接重置、读超时等）
     * 且请求幂等（GET/HEAD）时自动重试。非幂等请求不重试，避免重复提交。
     */
    private okhttp3.Interceptor retryOnIoErrorInterceptor() {
        return chain -> {
            Request request = chain.request();
            if (!request.method().equals("GET") && !request.method().equals("HEAD")) {
                return chain.proceed(request);
            }
            int maxAttempts = httpRetryOnIoError + 1;
            for (int attempt = 1; ; attempt++) {
                Response response;
                try {
                    response = chain.proceed(request);
                    // 5xx 也视为瞬时错误，幂等请求重试
                    if (response.code() >= 500 && attempt < maxAttempts) {
                        response.close();
                        continue;
                    }
                    return response;
                } catch (java.io.IOException e) {
                    if (attempt >= maxAttempts) throw e;
                    log.debug("瞬时网络错误，重试 {}/{}: {} - {}",
                            attempt, maxAttempts - 1, request.url(), e.getMessage());
                }
            }
        };
    }

    /**
     * 解析爬虫配置的自定义 Header：每行一个 "名称: 值"，以 # 开头的行为注释，
     * 空行/无效行忽略。解析失败时返回空列表（不影响请求）。
     */
    static List<Map.Entry<String, String>> parseConfiguredHeaders(String headers) {
        List<Map.Entry<String, String>> result = new java.util.ArrayList<>();
        if (headers == null || headers.isBlank()) {
            return result;
        }
        for (String rawLine : headers.split("\\R")) {
            String line = rawLine.split("#", 2)[0].trim();
            if (line.isEmpty()) {
                continue;
            }
            int idx = line.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            String name = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            if (name.isEmpty() || value.isEmpty()) {
                continue;
            }
            result.add(new java.util.AbstractMap.SimpleImmutableEntry<>(name, value));
        }
        return result;
    }

    /**
     * 应用爬虫配置的自定义 Header 到指定请求；与内置 Header 冲突时，配置的 Header 优先。
     */
    static Request applyConfiguredHeaders(Request request, String headers) {
        List<Map.Entry<String, String>> headerList = parseConfiguredHeaders(headers);
        if (headerList.isEmpty()) {
            return request;
        }
        Request.Builder builder = request.newBuilder();
        for (Map.Entry<String, String> entry : headerList) {
            builder.header(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    private Request buildRequest(String url, TaskMessage msg) {
        // 自定义 Header 由 httpClient(msg) 上的 Interceptor 统一注入（含重定向跳数），此处只设内置 Header
        return new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CollectX/1.0")
                .build();
    }

    /**
     * 构建 HTTP 客户端，池/超时/Dispatcher 配置取自 {@code app.http.*}
     * （{@link PoolSettings}）；池大小 <=0 表示禁用连接池，
     * Dispatcher 上限 <=0 表示沿用 OkHttp 默认值。
     *
     * <p>headers 不为空时，通过 Interceptor 把自定义 Header 注入到
     * 每一次请求（含 OkHttp 自动跟随的 302 重定向跳数）——
     * OkHttp 原生 followRedirects 跟随重定向时会丢弃自定义 Header，
     * 导致"第一次请求带了 Header、跟随重定向的那次没带"，目标站点
     * 因此判定未登录而返回登录页。Interceptor 方案可让自定义 Header
     * 在所有跳数上一致生效。
     */
    static OkHttpClient buildHttpClient(boolean skipTlsVerify,
                                        String headers,
                                        PoolSettings settings) {
        return buildHttpClient(skipTlsVerify, headers, settings, null);
    }

    /**
     * 构建 HTTP 客户端。{@code sharedPool} 不为 null 时所有客户端共享同一连接池
     * （不同 header/TLS 组合的客户端可复用同一条 TCP 连接）；为 null 时按
     * {@code settings} 自建独立连接池（测试场景）。
     *
     * <p>headers 不为空时，通过 Interceptor 把自定义 Header 注入到
     * 每一次请求（含 OkHttp 自动跟随的 302 重定向跳数）——
     * OkHttp 原生 followRedirects 跟随重定向时会丢弃自定义 Header，
     * 导致"第一次请求带了 Header、跟随重定向的那次没带"，目标站点
     * 因此判定未登录而返回登录页。Interceptor 方案可让自定义 Header
     * 在所有跳数上一致生效。
     */
    static OkHttpClient buildHttpClient(boolean skipTlsVerify,
                                        String headers,
                                        PoolSettings settings,
                                        ConnectionPool sharedPool) {
        List<Map.Entry<String, String>> headerList = parseConfiguredHeaders(headers);
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(Math.max(1, settings.connectTimeoutSeconds()), TimeUnit.SECONDS)
                .readTimeout(Math.max(1, settings.readTimeoutSeconds()), TimeUnit.SECONDS)
                .followRedirects(true)
                .connectionPool(sharedPool != null ? sharedPool
                        : new ConnectionPool(
                                Math.max(0, settings.maxIdleConnections()),
                                Math.max(1, settings.keepAliveMinutes()), TimeUnit.MINUTES));
        if (settings.maxConcurrentRequests() > 0) {
            Dispatcher dispatcher = new Dispatcher();
            dispatcher.setMaxRequests(settings.maxConcurrentRequests());
            if (settings.maxConcurrentRequestsPerClient() > 0) {
                dispatcher.setMaxRequestsPerHost(settings.maxConcurrentRequestsPerClient());
            }
            builder.dispatcher(dispatcher);
        }
        if (!headerList.isEmpty()) {
            builder.addInterceptor(chain -> {
                Request.Builder reqBuilder = chain.request().newBuilder();
                for (Map.Entry<String, String> entry : headerList) {
                    reqBuilder.header(entry.getKey(), entry.getValue());
                }
                return chain.proceed(reqBuilder.build());
            });
        }
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
        // 优先使用线程本地的任务级客户端（带独立 Dispatcher，调度隔离）
        OkHttpClient taskClient = currentTaskClient.get();
        if (taskClient != null) {
            return taskClient;
        }
        // 非爬取线程（如 robots 预加载）回退到共享客户端
        boolean skipTls = Integer.valueOf(1).equals(msg.getSkipTlsVerify());
        String headers = msg.getHeaders();
        if (headers == null || headers.isBlank()) {
            return skipTls ? unverifiedHttpClient : verifiedHttpClient;
        }
        String key = (skipTls ? "unverified" : "verified") + "|" + headers;
        return httpClientCache.computeIfAbsent(key, k ->
                buildClientWithDnsAndRetry(buildHttpClient(skipTls, headers, poolSettings(), sharedConnectionPool)));
    }

    /**
     * 连接池监控日志：每 {@code app.http.pool-metrics-log-seconds} 秒打印一次
     * 活跃/排队请求数、缓存客户端数，便于生产定位连接泄漏。
     * 间隔 <=0 时不打印（避免空转）。
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString =
            "${app.http.pool-metrics-log-seconds:60000}")
    public void logPoolMetrics() {
        if (httpPoolMetricsLogSeconds <= 0) return;
        int running = verifiedHttpClient.dispatcher().runningCallsCount();
        int queued = verifiedHttpClient.dispatcher().queuedCallsCount();
        log.debug("http-pool: running={}, queued={}, cachedClients={}, dnsCacheEntries={}, robotsCacheEntries={}",
                running, queued, httpClientCache.size(), dnsCache.size(), robotsCacheByHost.size());
    }

    /**
     * 应用关闭时释放连接池：取消空闲连接、停止 Dispatcher 的后台线程。
     * 未关闭时，连接池的空闲连接要等 keep-alive 超时才释放，Dispatcher 线程也会存活到进程退出。
     */
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        List<OkHttpClient> all = new java.util.ArrayList<>(httpClientCache.values());
        all.add(verifiedHttpClient);
        all.add(unverifiedHttpClient);
        for (OkHttpClient client : all) {
            client.connectionPool().evictAll();
            client.dispatcher().executorService().shutdown();
        }
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
        // 任务级共享图片下载线程池 + 限流信号量：图片并发计入任务总并发，
        // 保证每任务线程数不超过 urlConcurrency 配置
        final ExecutorService imageExecutor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        final java.util.concurrent.Semaphore imageSemaphore =
                new java.util.concurrent.Semaphore(concurrency);
        // 方案 A：任务级 Dispatcher（连接池共享 + 调度隔离），任务结束即释放
        final OkHttpClient taskClient = taskClientFor(msg);
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
                if (isExcludedUrl(normalizedStartUrl, msg)) {
                    log.info("起始URL命中排除规则，跳过: {}", normalizedStartUrl);
                    continue;
                }
                urlQueue.enqueueIfAbsent(taskId, normalizedStartUrl, 0);
            }
            // 读取缓存模式下，起始URL也优先从缓存发现：MinIO 中与已配置起始URL同域名的HTML对象
            if (Integer.valueOf(1).equals(msg.getReadCache())) {
                enqueueCachedUrlsByDomain(taskId, msg.getStartUrls(), msg);
            }

            while (true) {
                // 任务状态不是运行中（如被取消）时停止爬取（带 1s 本地缓存，减少 DB 查询）
                TaskStatusInfo status = getCachedTaskStatus(task.getId());
                if (status.status() == null || !"RUNNING".equals(status.status())) {
                    log.info("任务状态不是运行中，停止爬取: taskId={}, status={}", taskId, status.status());
                    break;
                }
                // 任务被暂停时等待，直到恢复或取消（不退出循环，恢复后继续爬取剩余队列）
                if (status.pausedAt() != null) {
                    awaitAnyFuture(activeFutures, TASK_CANCEL_POLL_INTERVAL_MS);
                    TaskStatusInfo check = getCachedTaskStatus(task.getId());
                    if (check.status() == null || !"RUNNING".equals(check.status())) {
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
                    TaskStatusInfo check = getCachedTaskStatus(task.getId());
                    if (check.status() == null || !"RUNNING".equals(check.status())) {
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
                    TaskStatusInfo check = getCachedTaskStatus(task.getId());
                    if (check.status() == null || !"RUNNING".equals(check.status())) {
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
                                robotsCache, processedResourceUrls, cachedStartUrls, execution, taskClient,
                                imageExecutor, imageSemaphore)
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
            // 释放任务级 Dispatcher 的后台线程（连接池仍由共享池管理，不在此释放）
            taskClient.dispatcher().executorService().shutdown();
            // 释放任务级图片下载线程池
            if (!awaitExecutorTermination(imageExecutor, 30, TimeUnit.SECONDS)) {
                imageExecutor.shutdownNow();
            }
        }

        LocalDateTime endTime = LocalDateTime.now();
        // 任务结束，清除状态缓存
        taskStatusCache.remove(taskId);
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

    /** 任务状态缓存 TTL（毫秒）：1 秒，状态变化最坏延迟 1s（可接受） */
    private static final long TASK_STATUS_CACHE_TTL_MS = 1000;

    /**
     * 带本地缓存的任务状态查询，TTL 1 秒。
     * 主循环每 500ms 轮询一次，原实现每轮查 1-3 次 DB，
     * 优化后每 1s 最多查 1 次 DB，查询量降低 ~80%。
     */
    private TaskStatusInfo getCachedTaskStatus(Long taskId) {
        long now = System.currentTimeMillis();
        TaskStatusCache cache = taskStatusCache.get(taskId);
        if (cache != null && cache.expireAt() > now) {
            return new TaskStatusInfo(cache.status(), cache.pausedAt());
        }
        SpiderTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return new TaskStatusInfo(null, null);
        }
        taskStatusCache.put(taskId, new TaskStatusCache(
                task.getStatus(), task.getPausedAt(), now + TASK_STATUS_CACHE_TTL_MS));
        // 防止缓存无限增长
        if (taskStatusCache.size() > 1024) {
            taskStatusCache.entrySet().removeIf(e -> e.getValue().expireAt() < now);
        }
        return new TaskStatusInfo(task.getStatus(), task.getPausedAt());
    }

    record TaskStatusInfo(String status, java.time.LocalDateTime pausedAt) {}

    private boolean awaitAnyFuture(Set<Future<?>> futures, long timeoutMs) {
        if (futures.isEmpty()) return true;
        // 快速路径：先检查一次，避免不必要的 sleep
        for (Future<?> f : futures) {
            if (f.isDone()) return true;
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            try {
                Thread.sleep(10); // 10ms 轮询间隔（原 100ms），减少等待延迟
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return true;
            }
            for (Future<?> f : futures) {
                if (f.isDone()) return true;
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
                           TaskExecutionContext execution, OkHttpClient taskClient,
                           java.util.concurrent.ExecutorService imageExecutor,
                           java.util.concurrent.Semaphore imageSemaphore) {
        currentTaskClient.set(taskClient);
        try {
            // 任务状态不是运行中（如被取消）时停止爬取（带 1s 本地缓存）
            TaskStatusInfo status = getCachedTaskStatus(task.getId());
            if (status.status() == null || !"RUNNING".equals(status.status())) {
                log.info("任务状态不是运行中，停止爬取: taskId={}, url={}, status={}", taskId, url, status.status());
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
                            "html/" + ObjectNameUtils.hashUrl(url) + ".html")
                    : null;
            boolean cacheHit = cachedHtml != null;
            if (cacheHit) {
                cachedStartUrls.add(url);
            }
            if (readCache && !cacheHit && !Integer.valueOf(1).equals(msg.getReadCacheMissOnline())) {
                // 读取缓存模式且未开启"未命中联网"：只从 MinIO 获取，未缓存的页面直接跳过
                writeLog(task.getId(), msg.getSpiderId(), url, 2, "INFO",
                        "缓存未命中，跳过（未开启未命中联网）", 0);
                log.info("读取缓存模式，缓存未命中，跳过: url={}", url);
                success.incrementAndGet();
                return;
            }
            Html doc = cacheHit ? new Html(cachedHtml, url) : fetch(url, msg, execution);
            long cost = System.currentTimeMillis() - start;

            ContentParser parsed = ContentParser.parse(doc.get(), url, msg.getContentSelector());
            String newHtml = doc.get();
            String newHtmlHash = md5(newHtml);
            // 读取缓存命中：内容视为已存在且未变化，跳过 ES 变更比对（不联网）
            boolean overwriteHtml = cacheHit || Integer.valueOf(1).equals(msg.getOverwriteHtml());
            boolean overwriteImage = !cacheHit && Integer.valueOf(1).equals(msg.getOverwriteImage());

            // 读取缓存时允许从已缓存页面扩展 URL；未命中联网=跳过时，
            // 扩展出的未缓存 URL 入队后会在处理阶段被跳过，不联网。
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
                    if (isExcludedUrl(normalizedNextUrl, msg)) continue;
                    if (!isAllowedByRobots(normalizedNextUrl, msg, robotsCache, execution)) continue;
                    if (urlQueue.enqueueIfAbsent(taskId, normalizedNextUrl, depth + 1)) enqueued++;
                }
                log.info("depth={}, url={}, extracted={}, enqueued={}", depth, url, next.size(), enqueued);
            }

            // 仅未命中缓存的页面才做 ES 变更比对（需要读取 MinIO 旧 HTML）；
            // 缓存命中时直接按"内容未变化"处理，避免额外网络/存储查询
            // overwriteHtml=1 时跳过变更比对（直接覆盖，无需读取旧内容）
            boolean contentUnchanged = false;
            SpiderContentDoc existingDoc = null;
            boolean overwriteHtmlFlag = Integer.valueOf(1).equals(msg.getOverwriteHtml());
            if (!cacheHit && !overwriteHtmlFlag && !isConfiguredStartUrl(url, msg.getStartUrls())) {
                // 配置中的起始 URL 始终抓取；仅其他页面检查已有内容是否需要跳过。
                CriteriaQuery criteriaQuery = new CriteriaQuery(new Criteria("url").is(url));
                SearchHits<SpiderContentDoc> existing = elasticsearchOperations.search(
                        criteriaQuery, SpiderContentDoc.class, IndexCoordinates.of(contentIndex));
                existingDoc = existing.isEmpty() ? null : existing.getSearchHits().get(0).getContent();
                if (existingDoc != null) {
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
            boolean isOverwrite = existingDoc != null || overwriteHtmlFlag;
            String crawlTime = isOverwrite && existingDoc != null && existingDoc.getCrawlTime() != null
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
                    msg, task, overwriteImage, execution, imageExecutor, imageSemaphore);
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
        } finally {
            currentTaskClient.remove();
        }
    }

    private void updateImagesAfterPageProcessing(Html doc, SpiderContentDoc document, String pageUrl,
                                                String title, TaskMessage msg, SpiderTask task,
                                                boolean overwrite, TaskExecutionContext execution,
                                                java.util.concurrent.ExecutorService imageExecutor,
                                                java.util.concurrent.Semaphore imageSemaphore) {
        try {
            List<String> imageUrls = shouldSkipImageDownload(doc, msg)
                    ? List.of()
                    : extractAndUploadImages(doc, pageUrl, title, msg, task, overwrite, execution,
                            imageExecutor, imageSemaphore);
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

    /**
     * 判断 URL 是否命中爬虫配置的排除规则。支持：
     * - 精确匹配（归一化后相等）
     * - 路径前缀匹配（如 "/tags/" 排除该目录下所有页面）
     * 排除规则来自 TaskMessage.excludedUrls（JSON 数组），按字符串缓存解析结果。
     */
    private boolean isExcludedUrl(String url, TaskMessage msg) {
        List<String> rules = getExcludedRules(msg);
        if (rules.isEmpty()) {
            return false;
        }
        String normalized = UrlQueueService.normalizeUrl(url);
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        for (String rule : rules) {
            String normalizedRule = UrlQueueService.normalizeUrl(rule);
            if (normalizedRule == null || normalizedRule.isBlank()) {
                continue;
            }
            if (normalized.equals(normalizedRule)) {
                return true;
            }
            if (normalized.startsWith(normalizedRule)) {
                return true;
            }
        }
        return false;
    }

    private List<String> getExcludedRules(TaskMessage msg) {
        String raw = msg.getExcludedUrls();
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return excludedUrlsCache.computeIfAbsent(raw, r -> {
            try {
                List<String> parsed = JSON.parseArray(r, String.class);
                if (parsed == null) {
                    return List.of();
                }
                return parsed.stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(String::trim)
                        .toList();
            } catch (Exception e) {
                log.warn("解析 excludedUrls 失败，忽略排除规则: {}", r, e);
                return List.of();
            }
        });
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
    private void enqueueCachedUrlsByDomain(Long taskId, List<String> startUrls, TaskMessage msg) {
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
                String hashPart = objectName.substring("html/".length(), objectName.length() - ".html".length());
                FileMetadata metadata = fileMetadataMapper.selectByObject(htmlBucket, objectName);
                if (metadata == null || metadata.getSource() == null || metadata.getSource().isBlank()) {
                    continue;
                }
                String url = metadata.getSource();
                String host = extractHost(url);
                if (host == null || !hosts.contains(host)) {
                    continue;
                }
                String normalized = UrlQueueService.normalizeUrl(url);
                if (normalized != null && !normalized.isBlank() && !isExcludedUrl(normalized, msg)) {
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
        return host.toLowerCase(java.util.Locale.ROOT);
    }

    /** 进程级 robots 缓存 TTL（毫秒）：同一 authority 的 robots 规则 30 分钟内跨任务复用 */
    private static final long ROBOTS_CACHE_TTL_MS = 30 * 60 * 1000L;

    private boolean isAllowedByRobots(String url, TaskMessage msg, Map<String, RobotsRules> robotsCache,
                                     TaskExecutionContext execution) {
        // 读取缓存模式且未开启"未命中联网"时不联网：不请求 robots.txt
        if (Integer.valueOf(1).equals(msg.getReadCache())
                && !Integer.valueOf(1).equals(msg.getReadCacheMissOnline())) {
            return true;
        }
        if (!Integer.valueOf(1).equals(msg.getFollowRobots())) return true;
        try {
            URI uri = URI.create(url);
            if (uri.getHost() == null) return false;
            String authority = uri.getAuthority();
            if (authority == null) return false;
            // 进程级缓存：按 authority 复用（同一 authority 下路径规则一致），TTL 30 分钟
            long now = System.currentTimeMillis();
            RobotsCacheEntry entry = robotsCacheByHost.get(authority);
            if (entry != null && entry.expireAt() > now) {
                return entry.rules().isAllowed(uri.getRawPath());
            }
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
            String robotsUrl = scheme + "://" + authority + "/robots.txt";
            RobotsRules rules = loadRobotsRules(robotsUrl, msg, execution);
            robotsCacheByHost.put(authority, new RobotsCacheEntry(rules, now + ROBOTS_CACHE_TTL_MS));
            // 防止缓存无限增长
            if (robotsCacheByHost.size() > 4096) {
                robotsCacheByHost.entrySet().removeIf(e -> e.getValue().expireAt() < now);
            }
            return rules.isAllowed(uri.getRawPath());
        } catch (Exception e) {
            log.debug("robots.txt 检查失败，放行 URL: {}", url, e);
            return true;
        }
    }

    private RobotsRules loadRobotsRules(String robotsUrl, TaskMessage msg, TaskExecutionContext execution) {
        try {
            Request request = buildRequest(robotsUrl, msg);
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
                                                TaskExecutionContext execution,
                                                java.util.concurrent.ExecutorService imageExecutor,
                                                java.util.concurrent.Semaphore imageSemaphore) {
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
            // 并行下载图片：使用任务级共享 Semaphore（= urlConcurrency）限流，
            // 图片线程计入任务总并发，保证每任务线程数不超过配置值
            java.util.List<java.util.concurrent.CompletableFuture<Void>> imageFutures = new java.util.ArrayList<>();
            java.util.concurrent.atomic.AtomicInteger uploadedCounter = new java.util.concurrent.atomic.AtomicInteger();
            java.util.concurrent.atomic.AtomicInteger skippedCounter = new java.util.concurrent.atomic.AtomicInteger();
            java.util.List<String> uploadedUrlsSync = java.util.Collections.synchronizedList(uploadedUrls);
            for (String src : imageSources) {
                    if (execution.isCancelled()) break;
                    if (src.isBlank() || !seen.add(src)) {
                        continue;
                    }
                    imageSemaphore.acquireUninterruptibly();
                    if (execution.isCancelled()) {
                        imageSemaphore.release();
                        break;
                    }
                    final String imageSrc = src;
                    imageFutures.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
                        try {
                            String ext = guessExt(imageSrc, null);
                            String objectName = "images/" + ObjectNameUtils.hashUrl(imageSrc) + ext;
                            // 不覆盖时，若图片已存在则跳过下载
                            if (!overwrite && minioHelper.objectExists(imageBucket, objectName)) {
                                skippedCounter.incrementAndGet();
                                saveExistingFileMetadata(objectName, msg.getSpiderId(), title, imageSrc);
                                uploadedUrlsSync.add(objectName);
                                return;
                            }
                            byte[] data = downloadImage(imageSrc, msg, execution);
                            if (execution.isCancelled() || data == null || data.length == 0) {
                                return;
                            }
                            // 下载后若扩展名与魔数判断不一致，则用实际扩展名重新命名
                            String realExt = guessExt(imageSrc, data);
                            if (!realExt.equals(ext)) {
                                objectName = "images/" + ObjectNameUtils.hashUrl(imageSrc) + realExt;
                                if (!overwrite && minioHelper.objectExists(imageBucket, objectName)) {
                                    skippedCounter.incrementAndGet();
                                    saveExistingFileMetadata(objectName, msg.getSpiderId(), title, imageSrc);
                                    uploadedUrlsSync.add(objectName);
                                    return;
                                }
                            }
                            // 覆盖模式下 putObject 会直接覆盖已存在的对象
                            minioHelper.putImage(imageBucket, objectName, data, guessContentType(realExt));
                            saveFileMetadata(imageBucket, objectName, data.length, guessContentType(realExt), "image",
                                msg.getSpiderId(), title, pageUrl);
                            uploadedCounter.incrementAndGet();
                            // 仅存储 MinIO 相对路径（objectName），前端通过后端接口按 objectName 获取图片
                            uploadedUrlsSync.add(objectName);
                        } catch (Exception e) {
                            if (!execution.isCancelled()) {
                                log.warn("图片下载/上传失败: src={}", imageSrc, e);
                            }
                        } finally {
                            imageSemaphore.release();
                        }
                    }, imageExecutor));
            }
            // 等待所有图片下载完成
            for (java.util.concurrent.CompletableFuture<Void> f : imageFutures) {
                try {
                    f.get(60, java.util.concurrent.TimeUnit.SECONDS);
                } catch (java.util.concurrent.TimeoutException e) {
                    log.warn("图片下载超时，跳过剩余图片: url={}", pageUrl);
                    break;
                } catch (Exception e) {
                    if (execution.isCancelled()) break;
                    log.warn("图片处理异常: url={}", pageUrl, e);
                }
            }
            uploaded = uploadedCounter.get();
            skipped = skippedCounter.get();
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

    private static final java.util.regex.Pattern NOSCRPT_IMG_PATTERN =
            java.util.regex.Pattern.compile(
                    "<noscript\\b[^>]*>(?s)(.*?)(?:</noscript>|$)",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern IMG_SRC_PATTERN =
            java.util.regex.Pattern.compile(
                    "<img\\b[^>]*\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * 从 <noscript> 标签中用正则提取图片 src。
     * HTML 解析器把 noscript 内容当作文本节点处理，JS 选择器和 XPath 均无法匹配其中的 <img>，
     * 因此需要从原始 HTML 中直接正则提取。
     */
    static List<String> extractNoscriptImageSources(String rawHtml, String pageUrl) {
        List<String> result = new java.util.ArrayList<>();
        if (rawHtml == null || rawHtml.isEmpty()) {
            return result;
        }
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        var matcher = NOSCRPT_IMG_PATTERN.matcher(rawHtml);
        while (matcher.find()) {
            String content = matcher.group(1);
            if (content == null) continue;
            var imgMatcher = IMG_SRC_PATTERN.matcher(content);
            while (imgMatcher.find()) {
                String src = imgMatcher.group(1);
                addAbsoluteImageSource(seen, pageUrl, src);
            }
        }
        result.addAll(seen);
        return result;
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
                // 选择器未命中 DOM 元素时，回退到 noscript 正则提取
                // （选择器本意是定位内容区域，noscript 中的图片可能属于同一内容）
                return extractNoscriptImageSources(doc.get(), pageUrl);
            }
            if (hasXpath) {
                // 选择器 + XPath：在选择器命中的元素范围内按 XPath 定位图片
                for (Selectable scope : matched) {
                    for (String expression : xpaths) {
                        sources.addAll(extractImageSourcesByXpath(scope, pageUrl, expression));
                    }
                }
            } else {
                // 仅选择器：取命中元素自身及内部的 <img>
                for (Selectable scope : matched) {
                    for (String source : scope.xpath("//img/@src").all()) {
                        addAbsoluteImageSource(sources, pageUrl, source);
                    }
                }
            }
        } else {
            // 仅 XPath：直接在整页范围内按 XPath 定位图片
            for (String expression : xpaths) {
                sources.addAll(extractImageSourcesByXpath(doc, pageUrl, expression));
            }
        }

        // 回退：DOM 匹配无结果时，从 noscript 正则提取
        if (sources.isEmpty()) {
            sources.addAll(extractNoscriptImageSources(doc.get(), pageUrl));
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
     * 对象名基于 URL 的 MD5 哈希；资源是否覆盖与 HTML 使用同一个覆盖开关。
     * saveResources=false 时（读取缓存命中且未开启覆盖HTML）只处理 HTML，
     * 页面引用的 JS/CSS 全部直接从 MinIO 复用，不回源。
     */
    private void saveHtmlAndJs(Html doc, String url, String html, TaskMessage msg,
                               SpiderTask task, boolean saveHtml, boolean saveResources,
                               Set<String> processedResourceUrls,
                               TaskExecutionContext execution) {
        if (execution.isCancelled()) return;
        try {
            String urlHash = ObjectNameUtils.hashUrl(url);
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
                String objectName = "js/" + ObjectNameUtils.hashUrl(jsUrl)
                        + (extension.isEmpty() ? ".js" : extension);
                enqueueResource(resourceQueue, processedResourceUrls,
                        new WebResource(jsUrl, "js", objectName, "application/javascript"));
            }
            for (String source : doc.$("link[rel~=stylesheet][href]", "href").all()) {
                String cssUrl = resolveResourceUrl(url, source);
                if (cssUrl == null) {
                    continue;
                }
                String objectName = "css/" + ObjectNameUtils.hashUrl(cssUrl) + ".css";
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
                    "html/" + ObjectNameUtils.hashUrl(url) + ".html");
        } catch (Exception e) {
            log.debug("从 MinIO 读取 HTML 失败: url={}", url, e);
            return null;
        }
    }

    private byte[] downloadBytes(String src, TaskMessage msg, TaskExecutionContext execution) throws Exception {
        Request request = buildRequest(src, msg);
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
        Request request = buildRequest(src, msg);
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
        // 自定义 Header 由 httpClient(msg) 上的 Interceptor 统一注入（含重定向跳数），此处只设内置 Header
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
        Response raw = call.execute();
        Response response = wrapWithSizeLimit(raw);
        try {
            return reader.read(response);
        } finally {
            response.close();
            execution.unregister(call);
        }
    }

    /**
     * 给响应套一层大小上限：{@code app.http.max-response-bytes} > 0 时，
     * 超过上限的响应体在读取时抛 {@link IllegalStateException}，防止大页面撑爆堆内存。
     * 响应头/状态码不变，调用方无感知（只会在读 body 时失败）。
     */
    private Response wrapWithSizeLimit(Response original) throws java.io.IOException {
        if (httpMaxResponseBytes <= 0 || original.body() == null) {
            return original;
        }
        long contentLength = original.body().contentLength();
        if (contentLength > httpMaxResponseBytes) {
            // Content-Length 明确超限：直接拒绝，不读 body
            original.close();
            throw new IllegalStateException(
                    "HTTP 响应体超过大小上限 " + httpMaxResponseBytes + " bytes (Content-Length="
                            + contentLength + ")，已丢弃");
        }
        // Content-Length 未声明或 <= 上限时：流式读取到 Buffer，超限则截断
        ResponseBody sourceBody = original.body();
        final long limit = httpMaxResponseBytes;
        final okio.Buffer buf = new okio.Buffer();
        final okio.BufferedSource src = sourceBody.source();
        try {
            while (true) {
                long remaining = limit - buf.size();
                if (remaining <= 0) break;
                long n = src.read(buf, remaining);
                if (n == -1) break;
            }
        } catch (java.io.IOException e) {
            original.close();
            throw e;
        }
        original.close();
        ResponseBody limitedBody = new ResponseBody() {
            @Override
            public okhttp3.MediaType contentType() { return sourceBody.contentType(); }

            @Override
            public long contentLength() { return buf.size(); }

            @Override
            public okio.BufferedSource source() { return buf; }
        };
        return original.newBuilder().body(limitedBody).build();
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
            // 异步发送：不阻塞爬取线程，Kafka 内部有批量 flush 机制
            kafkaTemplate.send(taskLogTopic, JSON.toJSONString(logMsg))
                    .exceptionally(ex -> {
                        log.warn("任务日志发送 Kafka 失败: taskId={}, url={}", taskId, url, ex);
                        return null;
                    });
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
