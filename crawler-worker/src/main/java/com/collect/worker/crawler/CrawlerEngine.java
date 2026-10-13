package com.collect.worker.crawler;

import com.alibaba.fastjson2.JSON;
import com.collect.common.mq.TaskLogMessage;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.Protocol;
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
    /** 响应体大小上限（字节）；0=不限制。超限时拒绝处理，防止大页面撑爆堆内存 */
    @Value("${app.http.max-response-bytes:10485760}")
    private long httpMaxResponseBytes;
    /** HTTP 协议偏好：h2=允许 HTTP/2，http1=禁用 HTTP/2 */
    @Value("${app.http.protocol:http1}")
    private String httpProtocol;
    /** 瞬时网络错误（IOException）重试次数，仅对幂等请求生效；0=不重试 */
    @Value("${app.http.retry-on-io-error:2}")
    private int httpRetryOnIoError;
    /** 本地 DNS 缓存 TTL（秒）；0=禁用 DNS 缓存。爬取多域名时避免重复 DNS 查询 */
    @Value("${app.http.dns-cache-ttl-seconds:300}")
    private long httpDnsCacheTtlSeconds;
    /** 连接池监控日志间隔（秒）；0=关闭 */
    @Value("${app.http.pool-metrics-log-seconds:60}")
    private int httpPoolMetricsLogSeconds;

    /** 所有客户端共享的连接池：不同 header/TLS 组合的客户端复用同一条 TCP 连接 */
    private ConnectionPool sharedConnectionPool;
    /** 进程级 robots 缓存（按 authority），跨任务复用，TTL 30 分钟，LRU 上限 4096 条 */
    private final Map<String, RobotsCacheEntry> robotsCacheByHost = buildLruCache(4096);
    /** 本地 DNS 缓存（name -> (addresses, expireAt)），线程安全 LRU，上限 1024 条 */
    private final Map<String, DnsCacheEntry> dnsCache = buildLruCache(1024);
    /** 任务级客户端（线程本地）：每个爬取虚拟线程绑定自己任务的 Dispatcher，实现调度隔离 */
    private final ThreadLocal<OkHttpClient> currentTaskClient = new ThreadLocal<>();
    /** 任务状态本地缓存（taskId -> (status, pausedAt, expireAt)），TTL 1s，LRU 上限 1024 条 */
    private final Map<Long, TaskStatusCache> taskStatusCache = buildLruCache(1024);
    /**
     * 进程级 URL→htmlHash 缓存：记录每个 URL 最近一次处理时的内容哈希（带 TTL）。
     * 重爬时若某 URL 的 newHtmlHash 与缓存值相同，说明内容未变，可跳过 ES 变更比对
     * （省去一次 ES 查询）。TTL 防止站点内容长期变化后缓存值过期导致误判。
     * LRU 上限 100k 条（每条约 200 字节，峰值 ~20MB）。
     */
    private final Map<String, UrlHashCacheEntry> urlHtmlHashCache = buildLruCache(100_000);
    /** URL 哈希缓存 TTL（毫秒）：24 小时，与 Redis URL 队列 TTL 对齐 */
    private static final long URL_HASH_CACHE_TTL_MS = 24 * 60 * 60 * 1000L;

    record WebResource(String url, String category, String objectName, String contentType) {}
    /** URL 内容哈希缓存条目：hash + 过期时间戳 */
    record UrlHashCacheEntry(String htmlHash, long expireAt) {}

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
    private final ElasticsearchOperations elasticsearchOperations;
    private final UrlQueueService urlQueue;
    private final MinioHelper minioHelper;
    private final FileMetadataMapper fileMetadataMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private OkHttpClient verifiedHttpClient;
    private OkHttpClient unverifiedHttpClient;
    /** 带自定义 Header 的客户端缓存：LRU 上限 128 条，淘汰最久未使用的 Header 组合 */
    private final java.util.Map<String, OkHttpClient> httpClientCache = buildLruCache(128);
    private final java.util.Map<String, ExcludedRules> excludedUrlsCache = new ConcurrentHashMap<>();

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
                         ElasticsearchOperations elasticsearchOperations, UrlQueueService urlQueue,
                         MinioHelper minioHelper, FileMetadataMapper fileMetadataMapper,
                         KafkaTemplate<String, String> kafkaTemplate) {
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
        this.elasticsearchOperations = elasticsearchOperations;
        this.urlQueue = urlQueue;
        this.minioHelper = minioHelper;
        this.fileMetadataMapper = fileMetadataMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    @jakarta.annotation.PostConstruct
    void initializeHttpClients() {
        this.sharedConnectionPool = new ConnectionPool(
                Math.max(0, httpMaxIdleConnections),
                Math.max(1, httpKeepAliveMinutes), TimeUnit.MINUTES);
        this.verifiedHttpClient = buildClientWithDnsAndRetry(
                buildHttpClient(false, null, poolSettings(), sharedConnectionPool, httpProtocol));
        this.unverifiedHttpClient = buildClientWithDnsAndRetry(
                buildHttpClient(true, null, poolSettings(), sharedConnectionPool, httpProtocol));
        log.info("HTTP 客户端初始化: protocol={}, connectTimeout={}s, readTimeout={}s, ioRetries={}",
                httpProtocol, httpConnectTimeoutSeconds, httpReadTimeoutSeconds, httpRetryOnIoError);
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
     * 获取任务对应的 HTTP 客户端（不创建新实例，复用共享客户端）。
     *
     * <p>不再按任务派生独立 Dispatcher 的 OkHttpClient（旧方案 A：连接池共享 + 调度隔离）。
     * OkHttp 文档明确 "It is an error to build a new client with an existing dispatcher"，
     * 且任务结束后 OkHttpClient 对象本身会留在堆里等 GC。现改为复用共享客户端
     * （含 TLS / 自定义 Header 组合），并发由 execute() 里的 imageSemaphore（= urlConcurrency）
     * 统一限流，避免每任务一个废弃的 OkHttp 实例。
     */
    private OkHttpClient taskClientFor(TaskMessage msg) {
        return httpClient(msg);
    }

    /**
     * 构建线程安全的 LRU 缓存：底层是 {@link LinkedHashMap}（accessOrder=true）
     * 包装在 {@link java.util.Collections#synchronizedMap} 中，容量超出时自动淘汰最久未访问的条目。
     * 避免 {@code clear()} 全量清除导致的缓存抖动。
     */
    private static <K, V> Map<K, V> buildLruCache(int maxSize) {
        LinkedHashMap<K, V> lru = new LinkedHashMap<>(
                Math.max(16, maxSize / 2), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
                return size() > maxSize;
            }
        };
        return java.util.Collections.synchronizedMap(lru);
    }

    /** 带本地 TTL 缓存的 DNS 解析器；缓存条目按 host 去重，LRU 自动淘汰，过期自动清除 */
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
            return addresses;
        };
    }

    /** 指数退避基础延迟（毫秒） */
    private static final long RETRY_BACKOFF_BASE_MS = 200;
    /** 指数退避上限（毫秒） */
    private static final long RETRY_BACKOFF_MAX_MS = 5000;
    /** Retry-After 头解析上限（秒），防止恶意超大值阻塞线程 */
    private static final int RETRY_AFTER_MAX_SECONDS = 30;

    /**
     * 瞬时网络错误重试：{@link java.io.IOException}（连接重置、读超时等）
     * 且请求幂等（GET/HEAD）时自动重试，带指数退避 + 抖动。
     * 5xx 响应优先读取 Retry-After 头，否则按退避间隔等待。
     * 非幂等请求不重试，避免重复提交。
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
                    // 5xx 视为瞬时错误，幂等请求重试（带退避）
                    if (response.code() >= 500 && attempt < maxAttempts) {
                        long backoffMs = computeBackoff(request, response, attempt);
                        log.debug("HTTP {} 重试 {}/{}: {} (等待 {}ms)",
                                response.code(), attempt, maxAttempts - 1, request.url(), backoffMs);
                        response.close();
                        try {
                            Thread.sleep(backoffMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new java.io.IOException("重试等待被中断", ie);
                        }
                        continue;
                    }
                    return response;
                } catch (java.io.IOException e) {
                    if (attempt >= maxAttempts) throw e;
                    long backoffMs = backoffWithJitter(attempt);
                    log.debug("瞬时网络错误，重试 {}/{}: {} - {} (等待 {}ms)",
                            attempt, maxAttempts - 1, request.url(), e.getMessage(), backoffMs);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        };
    }

    /**
     * 计算重试前等待时间：
     * 1. 优先读 Retry-After 头（429/503 时服务端可能设置）
     * 2. 否则按指数退避：base * 2^(attempt-1)，上限 maxMs，加随机抖动
     */
    private long computeBackoff(Request request, Response response, int attempt) {
        String retryAfter = response.header("Retry-After");
        if (retryAfter != null && !retryAfter.isBlank()) {
            try {
                int seconds = Integer.parseInt(retryAfter.trim());
                if (seconds > 0 && seconds <= RETRY_AFTER_MAX_SECONDS) {
                    return seconds * 1000L;
                }
                // 也可能是 HTTP 日期格式，尝试解析
                long httpDate = java.time.Instant.parse(retryAfter.trim()).toEpochMilli();
                long delay = httpDate - System.currentTimeMillis();
                if (delay > 0 && delay <= RETRY_AFTER_MAX_SECONDS * 1000L) {
                    return delay;
                }
            } catch (Exception ignored) {
                // 解析失败，回退到指数退避
            }
        }
        return backoffWithJitter(attempt);
    }

    /** 指数退避 + 随机抖动：base * 2^(attempt-1) * (0.5 + random*0.5) */
    private long backoffWithJitter(int attempt) {
        long base = RETRY_BACKOFF_BASE_MS * (1L << Math.min(attempt - 1, 12)); // 防溢出
        long capped = Math.min(base, RETRY_BACKOFF_MAX_MS);
        // 抖动范围 [0.5 * capped, 1.0 * capped]
        long jitter = (long) (capped * (0.5 + Math.random() * 0.5));
        return jitter;
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

    static List<Protocol> resolveHttpProtocols(String protocol) {
        if ("http1".equalsIgnoreCase(protocol) || "http/1.1".equalsIgnoreCase(protocol)) {
            return List.of(Protocol.HTTP_1_1);
        }
        return List.of(Protocol.HTTP_2, Protocol.HTTP_1_1);
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
        return buildHttpClient(skipTlsVerify, headers, settings, null, "http1");
    }

    static OkHttpClient buildHttpClient(boolean skipTlsVerify,
                                        String headers,
                                        PoolSettings settings,
                                        String httpProtocol) {
        return buildHttpClient(skipTlsVerify, headers, settings, null, httpProtocol);
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
                                        ConnectionPool sharedPool,
                                        String httpProtocol) {
        List<Map.Entry<String, String>> headerList = parseConfiguredHeaders(headers);
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(Math.max(1, settings.connectTimeoutSeconds()), TimeUnit.SECONDS)
                .readTimeout(Math.max(1, settings.readTimeoutSeconds()), TimeUnit.SECONDS)
                .writeTimeout(Math.max(1, settings.readTimeoutSeconds()), TimeUnit.SECONDS)
                .followRedirects(true)
                .protocols(resolveHttpProtocols(httpProtocol))
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
                buildClientWithDnsAndRetry(buildHttpClient(skipTls, headers, poolSettings(), sharedConnectionPool, httpProtocol)));
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
        // 任务级图片下载线程池：并行下载图片
        final ExecutorService imageExecutor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        final java.util.concurrent.Semaphore imageSemaphore =
                new java.util.concurrent.Semaphore(concurrency);
        // 任务级 HTTP 客户端：复用共享客户端（带独立 TLS/Header 组合），并发由 imageSemaphore(=concurrency) 限流
        final OkHttpClient taskClient = taskClientFor(msg);
        TaskExecutionContext execution = new TaskExecutionContext();
        // 预计算起始 URL 的归一化集合和 host 集合，crawlUrl 每页只做 O(1) 查找
        final StartUrlInfo startUrlInfo = buildStartUrlInfo(msg.getStartUrls());

        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger fail = new AtomicInteger(0);
        Set<String> processedResourceUrls = ConcurrentHashMap.newKeySet();
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
                String item = urlQueue.popAndClaim(taskId);
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
                int depth = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                final String crawlUrl = url;
                final int crawlDepth = depth;
                Future<?> future = executor.submit(() ->
                        crawlUrl(crawlUrl, crawlDepth, maxDepth, msg, task, taskId, success, fail,
                                processedResourceUrls, execution, taskClient,
                                imageExecutor, imageSemaphore, startUrlInfo)
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
        return new TaskStatusInfo(task.getStatus(), task.getPausedAt());
    }

    record TaskStatusInfo(String status, java.time.LocalDateTime pausedAt) {}

    /**
     * 任务级起始 URL 预计算信息：在 {@link #execute} 入口处一次性计算，
     * 避免 {@link #crawlUrl} 每页重复遍历 startUrls 做 URI 解析 + 归一化。
     */
    record StartUrlInfo(Set<String> normalizedSet, Set<String> hosts) {
        static StartUrlInfo empty() {
            return new StartUrlInfo(java.util.Collections.emptySet(), java.util.Collections.emptySet());
        }
    }

    /**
     * 等待至少一个 future 完成，或超时。
     * 轮询间隔 50ms：主循环已有 500ms 状态缓存做取消检查，50ms 足够响应
     * 并发槽位释放，同时避免 10ms 轮询在主线程上的空转开销。
     */
    private boolean awaitAnyFuture(Set<Future<?>> futures, long timeoutMs) {
        if (futures.isEmpty()) return true;
        // 快速路径：先检查一次，避免不必要的 sleep
        for (Future<?> f : futures) {
            if (f.isDone()) return true;
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            try {
                Thread.sleep(50);
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
                           Set<String> processedResourceUrls,
                           TaskExecutionContext execution, OkHttpClient taskClient,
                           java.util.concurrent.ExecutorService imageExecutor,
                           java.util.concurrent.Semaphore imageSemaphore,
                           StartUrlInfo startUrlInfo) {
        currentTaskClient.set(taskClient);
        try {
            // 任务状态不是运行中（如被取消）时停止爬取（带 1s 本地缓存）
            TaskStatusInfo status = getCachedTaskStatus(task.getId());
            if (status.status() == null || !"RUNNING".equals(status.status())) {
                log.info("任务状态不是运行中，停止爬取: taskId={}, url={}, status={}", taskId, url, status.status());
                return;
            }
            if (!isAllowedByRobots(url, msg, execution)) {
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

            ContentParser parsed = ContentParser.parse(doc, url, msg.getContentSelector());
            String newHtml = doc.get();
            String newHtmlHash = md5(newHtml);
            String pageTitle = parsed.getTitle();
            // 读取缓存命中：内容视为已存在且未变化，跳过 ES 变更比对（不联网）
            boolean overwriteHtml = cacheHit || Integer.valueOf(1).equals(msg.getOverwriteHtml());
            boolean overwriteImage = !cacheHit && Integer.valueOf(1).equals(msg.getOverwriteImage());

            // 读取缓存时允许从已缓存页面扩展 URL；未命中联网=跳过时，
            // 扩展出的未缓存 URL 入队后会在处理阶段被跳过，不联网。
            // extractNextUrls 已返回归一化后的 URL，此处只需做 O(1) 的 Set 查找。
            if (!msg.isSingleUrl() && depth < maxDepth) {
                List<String> next = ContentParser.extractNextUrls(doc, url, maxDepth - depth);
                next.removeIf(nextUrl ->
                        startUrlInfo.normalizedSet().contains(nextUrl) || !isSameDomain(nextUrl, startUrlInfo.hosts()));
                int enqueued = 0;
                for (String nextUrl : next) {
                    if (nextUrl == null || nextUrl.isBlank()) continue;
                    if (isExcludedUrl(nextUrl, msg)) continue;
                    if (!isAllowedByRobots(nextUrl, msg, execution)) continue;
                    if (urlQueue.enqueueIfAbsent(taskId, nextUrl, depth + 1)) enqueued++;
                }
                log.info("depth={}, url={}, extracted={}, enqueued={}", depth, url, next.size(), enqueued);
            }

            // 仅未命中缓存的页面才做 ES 变更比对；
            // 缓存命中时直接按"内容未变化"处理，避免额外网络/存储查询
            // overwriteHtml=1 时跳过变更比对（直接覆盖，无需读取旧内容）
            boolean contentUnchanged = false;
            SpiderContentDoc existingDoc = null;
            boolean overwriteHtmlFlag = Integer.valueOf(1).equals(msg.getOverwriteHtml());
            if (!cacheHit && !overwriteHtmlFlag && !isConfiguredStartUrl(url, startUrlInfo)) {
                // 快速路径：本地 hash 缓存命中且内容未变 → 跳过 ES 查询（省去一次 ES 往返）
                long nowMs = System.currentTimeMillis();
                UrlHashCacheEntry cached = urlHtmlHashCache.get(url);
                if (cached != null && cached.expireAt() > nowMs
                        && newHtmlHash.equals(cached.htmlHash())) {
                    contentUnchanged = true;
                } else {
                    // 慢路径：查 ES 做变更比对
                    CriteriaQuery criteriaQuery = new CriteriaQuery(new Criteria("url").is(url));
                    SearchHits<SpiderContentDoc> existing = elasticsearchOperations.search(
                            criteriaQuery, SpiderContentDoc.class, IndexCoordinates.of(contentIndex));
                    existingDoc = existing.isEmpty() ? null : existing.getSearchHits().get(0).getContent();
                    if (existingDoc != null) {
                        // 优先用 ES 中存储的 htmlHash 做 O(1) 比对，无需读取 MinIO 整份 HTML
                        if (existingDoc.getHtmlHash() != null && !existingDoc.getHtmlHash().isBlank()) {
                            contentUnchanged = newHtmlHash.equals(existingDoc.getHtmlHash());
                        } else {
                            // 旧数据无 htmlHash 字段：回退到读取 MinIO 旧 HTML 算 MD5 比对
                            String oldHtml = readHtmlFromMinio(url);
                            if (oldHtml != null && newHtmlHash.equals(md5(oldHtml))) {
                                contentUnchanged = true;
                            }
                        }
                    }
                }
            }

            // 后续页面不覆盖 HTML 且内容未变化 → 跳过
            if (!overwriteHtml && contentUnchanged) {
                writeLog(task.getId(), msg.getSpiderId(), url, 2, "INFO",
                        "已存在，跳过: " + pageTitle, (int) cost);
                log.info("内容未变化，跳过: url={}", url);
                // 刷新本地 hash 缓存（内容未变，保持新鲜以便下次快速跳过）
                urlHtmlHashCache.put(url, new UrlHashCacheEntry(newHtmlHash,
                        System.currentTimeMillis() + URL_HASH_CACHE_TTL_MS));
                success.incrementAndGet();
                return;
            }

            SpiderContentDoc docObj = new SpiderContentDoc();
            docObj.setId(md5(url));
            docObj.setTitle(pageTitle);
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
            docObj.setHtmlHash(newHtmlHash);

            saveToElasticsearchWithRetry(docObj);
            writeLog(task.getId(), msg.getSpiderId(), url, 1, "INFO",
                (overwriteHtml && existingDoc != null) ? "覆盖更新: " + pageTitle : "抓取成功: " + pageTitle, (int) cost);
            success.incrementAndGet();
            // 写回本地 hash 缓存：页面已写入 ES，缓存值与 ES 一致，重爬时可快速跳过 ES 查询
            urlHtmlHashCache.put(url, new UrlHashCacheEntry(newHtmlHash,
                    System.currentTimeMillis() + URL_HASH_CACHE_TTL_MS));

            // Persist the page before downloading static assets so slow asset hosts cannot delay it.
            // 读取缓存命中时，HTML 和 JS/CSS 资源都直接从 MinIO 复用，不再回源。
            boolean saveResources = !cacheHit || Integer.valueOf(1).equals(msg.getOverwriteHtml());
            saveHtmlAndJs(doc, url, newHtml, pageTitle, msg, task, !cacheHit, saveResources, processedResourceUrls, execution, imageExecutor, imageSemaphore);
            updateImagesAfterPageProcessing(doc, docObj, url, pageTitle,
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
        if ((msg.getImageSelector() == null || msg.getImageSelector().isBlank())
                && (msg.getImageXpath() == null || msg.getImageXpath().isBlank())) {
            return;
        }
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
     * 排除规则预计算结果：
     * - exactSet：归一化后精确匹配的 URL 集合，O(1) 查找
     * - prefixRules：归一化后需前缀匹配的规则列表，按长度倒序（长前缀优先命中）
     */
    record ExcludedRules(Set<String> exactSet, List<String> prefixRules) {
        static ExcludedRules empty() {
            return new ExcludedRules(java.util.Collections.emptySet(), java.util.Collections.emptyList());
        }
    }

    /**
     * 判断 URL 是否命中爬虫配置的排除规则。支持：
     * - 精确匹配（归一化后相等）→ O(1) Set 查找
     * - 路径前缀匹配（如 "/tags/" 排除该目录下所有页面）→ O(prefixCount) 线性扫描
     * - 文件路径包含匹配（如 "/wp-login.php" 排除带查询参数或嵌套路径的变体）
     * 排除规则来自 TaskMessage.excludedUrls（JSON 数组），预计算结果按原始字符串缓存。
     */
    private boolean isExcludedUrl(String url, TaskMessage msg) {
        ExcludedRules rules = getExcludedRules(msg);
        if (rules.exactSet().isEmpty() && rules.prefixRules().isEmpty()) {
            return false;
        }
        String normalized = UrlQueueService.normalizeUrl(url);
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        // 精确匹配：O(1)
        if (rules.exactSet().contains(normalized)) {
            return true;
        }
        // 前缀匹配：取 path+query 部分，边界感知匹配（前缀后必须是 / 或字符串结尾）
        if (!rules.prefixRules().isEmpty()) {
            String pathAndQuery = extractPathAndQuery(normalized);
            if (pathAndQuery != null) {
                for (String prefix : rules.prefixRules()) {
                    if ("/".equals(prefix)) {
                        // 根路径：所有路径都以 / 开头，直接命中
                        return true;
                    }
                    int lastSlash = prefix.lastIndexOf('/');
                    if (prefix.lastIndexOf('.') > lastSlash && pathAndQuery.contains(prefix)) {
                        return true;
                    }
                    if (pathAndQuery.startsWith(prefix)) {
                        int afterPrefix = pathAndQuery.length() - prefix.length();
                        // 前缀后必须是路径分隔符或已到末尾，避免 /tags 误拦 /tags2
                        if (afterPrefix == 0 || pathAndQuery.charAt(prefix.length()) == '/') {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * 从归一化后的完整 URL 中提取 path + query 部分。
     * 例如 "https://example.com/tags/foo?a=1" → "/tags/foo?a=1"
     */
    private String extractPathAndQuery(String normalizedUrl) {
        int schemeEnd = normalizedUrl.indexOf("://");
        if (schemeEnd < 0) return null;
        int pathStart = normalizedUrl.indexOf('/', schemeEnd + 3);
        if (pathStart < 0) return "/";
        return normalizedUrl.substring(pathStart);
    }

    private ExcludedRules getExcludedRules(TaskMessage msg) {
        String raw = msg.getExcludedUrls();
        if (raw == null || raw.isBlank()) {
            return ExcludedRules.empty();
        }
        return excludedUrlsCache.computeIfAbsent(raw, r -> {
            try {
                List<String> parsed = JSON.parseArray(r, String.class);
                if (parsed == null) {
                    return ExcludedRules.empty();
                }
                Set<String> exactSet = new java.util.HashSet<>();
                List<String> prefixRules = new java.util.ArrayList<>();
                for (String rule : parsed) {
                    if (rule == null || rule.isBlank()) continue;
                    String trimmed = rule.trim();
                    // 完整 URL（含 scheme://host）走精确匹配
                    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                        String normalizedRule = UrlQueueService.normalizeUrl(trimmed);
                        if (normalizedRule != null && !normalizedRule.isBlank()) {
                            exactSet.add(normalizedRule);
                        }
                    } else {
                        // 纯路径/前缀：去掉末尾斜杠后作为前缀规则；"/" 保留（表示排除所有）
                        String prefix = trimmed;
                        if (prefix.length() > 1 && prefix.endsWith("/")) {
                            prefix = prefix.substring(0, prefix.length() - 1);
                        }
                        if (!prefix.isBlank()) {
                            prefixRules.add(prefix);
                        }
                    }
                }
                // 前缀规则按长度倒序：长前缀优先命中，避免短前缀误拦截
                prefixRules.sort((a, b) -> b.length() - a.length());
                return new ExcludedRules(
                        java.util.Collections.unmodifiableSet(exactSet),
                        java.util.Collections.unmodifiableList(prefixRules));
            } catch (Exception e) {
                log.warn("解析 excludedUrls 失败，忽略排除规则: {}", r, e);
                return ExcludedRules.empty();
            }
        });
    }

    /**
     * 判断 URL 的 host 是否与起始 URL 的 host 集合相同。
     * host 集合在 {@link #buildStartUrlInfo} 中预计算，此处只做 O(1) 查找。
     */
    private boolean isSameDomain(String url, Set<String> startHosts) {
        String host = hostOf(url);
        return host != null && startHosts.contains(host);
    }

    /**
     * 判断 URL 是否为配置中的起始 URL（归一化后精确匹配）。
     * 归一化集合在 {@link #buildStartUrlInfo} 中预计算，此处只做 O(1) 查找。
     */
    private boolean isConfiguredStartUrl(String url, StartUrlInfo startUrlInfo) {
        String normalizedUrl = UrlQueueService.normalizeUrl(url);
        return normalizedUrl != null && startUrlInfo.normalizedSet().contains(normalizedUrl);
    }

    /**
     * 一次性预计算起始 URL 的归一化集合和 host 集合，供 {@link #crawlUrl} 每页 O(1) 查找。
     */
    private StartUrlInfo buildStartUrlInfo(List<String> startUrls) {
        if (startUrls == null || startUrls.isEmpty()) {
            return StartUrlInfo.empty();
        }
        Set<String> normalizedSet = java.util.concurrent.ConcurrentHashMap.newKeySet();
        Set<String> hostSet = java.util.concurrent.ConcurrentHashMap.newKeySet();
        for (String startUrl : startUrls) {
            String normalized = UrlQueueService.normalizeUrl(startUrl);
            if (normalized != null && !normalized.isBlank()) {
                normalizedSet.add(normalized);
            }
            String host = hostOf(startUrl);
            if (host != null) {
                hostSet.add(host);
            }
        }
        return new StartUrlInfo(java.util.Collections.unmodifiableSet(normalizedSet),
                java.util.Collections.unmodifiableSet(hostSet));
    }

    /**
     * 读取缓存模式：从 MinIO 的 html/ 目录发现已缓存的页面URL并入队。
     * 只收录与已配置起始URL同域名的缓存（避免跨域污染）。
     * 发现失败时记录日志，不影响任务。
     * 优化：批量查询 FileMetadata（按 objectName 列表），避免 N+1 查询。
     */
    private void enqueueCachedUrlsByDomain(Long taskId, List<String> startUrls, TaskMessage msg) {
        Set<String> hosts = new java.util.HashSet<>();
        for (String startUrl : startUrls) {
            String host = hostOf(startUrl);
            if (host != null) {
                hosts.add(host);
            }
        }
        if (hosts.isEmpty()) {
            return;
        }
        try {
            List<String> objectNames = minioHelper.listObjectNamesByPrefixAndSuffix(htmlBucket, "html/", ".html");
            if (objectNames.isEmpty()) {
                return;
            }
            // 批量查询：1 次 DB 查询替代 N 次
            List<FileMetadata> metadataList = fileMetadataMapper.selectByObjects(htmlBucket, objectNames);
            java.util.Map<String, String> objectToSource = new java.util.HashMap<>();
            for (FileMetadata m : metadataList) {
                if (m.getSource() != null && !m.getSource().isBlank()) {
                    objectToSource.put(m.getObjectName(), m.getSource());
                }
            }
            for (String objectName : objectNames) {
                String url = objectToSource.get(objectName);
                if (url == null) {
                    continue;
                }
                String host = hostOf(url);
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


    /**
     * 从 URL 提取小写 host；解析失败返回 null。
     * 合并了原 extractHost（URL→host）和 normalizeHost（host→lowercase）的职责。
     */
    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null || host.isBlank() ? null : host.toLowerCase(java.util.Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }

    /** 进程级 robots 缓存 TTL（毫秒）：同一 authority 的 robots 规则 30 分钟内跨任务复用 */
    private static final long ROBOTS_CACHE_TTL_MS = 30 * 60 * 1000L;

    private boolean isAllowedByRobots(String url, TaskMessage msg,
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
            return rules.isAllowed(uri.getRawPath());
        } catch (Exception e) {
            log.debug("robots.txt 检查失败，放行 URL: {}", url, e);
            return true;
        }
    }

    private RobotsRules loadRobotsRules(String robotsUrl, TaskMessage msg, TaskExecutionContext execution) {
        try {
            Request request = buildRequest(robotsUrl, msg);
            return executeRequest(httpClient(msg), request, execution, msg, response -> {
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

            java.util.Set<String> seen = new java.util.HashSet<>();
            // 并行下载图片：使用任务级共享 Semaphore（= urlConcurrency）限流，
            // 图片线程计入任务总并发。每个 future 返回 objectName（失败返回 null），
            // 按原始 imageSources 顺序收集结果，保证顺序不乱。
            java.util.List<java.util.concurrent.CompletableFuture<String>> imageFutures = new java.util.ArrayList<>();
            java.util.concurrent.atomic.AtomicInteger uploadedCounter = new java.util.concurrent.atomic.AtomicInteger();
            java.util.concurrent.atomic.AtomicInteger skippedCounter = new java.util.concurrent.atomic.AtomicInteger();
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
                imageFutures.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        String ext = guessExt(imageSrc, null);
                        String objectName = "images/" + ObjectNameUtils.hashUrl(imageSrc) + ext;
                        // 不覆盖时，若图片已存在则跳过下载
                        if (!overwrite && minioHelper.objectExists(imageBucket, objectName)) {
                            skippedCounter.incrementAndGet();
                            saveExistingFileMetadata(objectName, msg.getSpiderId(), title, imageSrc);
                            return objectName;
                        }
                        byte[] data = downloadBytes(imageSrc, msg, execution);
                        if (execution.isCancelled() || data == null || data.length == 0) {
                            return null;
                        }
                        // 下载后若扩展名与魔数判断不一致，则用实际扩展名重新命名
                        String realExt = guessExt(imageSrc, data);
                        if (!realExt.equals(ext)) {
                            objectName = "images/" + ObjectNameUtils.hashUrl(imageSrc) + realExt;
                            if (!overwrite && minioHelper.objectExists(imageBucket, objectName)) {
                                skippedCounter.incrementAndGet();
                                saveExistingFileMetadata(objectName, msg.getSpiderId(), title, imageSrc);
                                return objectName;
                            }
                        }
                        // 覆盖模式下 putObject 会直接覆盖已存在的对象
                        minioHelper.putImage(imageBucket, objectName, data, guessContentType(realExt));
                        saveFileMetadata(imageBucket, objectName, data.length, guessContentType(realExt), "image",
                            msg.getSpiderId(), title, pageUrl);
                        uploadedCounter.incrementAndGet();
                        // 仅存储 MinIO 相对路径（objectName），前端通过后端接口按 objectName 获取图片
                        return objectName;
                    } catch (Exception e) {
                        if (!execution.isCancelled()) {
                            log.warn("图片下载/上传失败: src={}", imageSrc, e);
                        }
                        return null;
                    } finally {
                        imageSemaphore.release();
                    }
                }, imageExecutor));
            }
            // 按原始顺序收集结果，保证图片顺序不乱
            for (java.util.concurrent.CompletableFuture<String> f : imageFutures) {
                try {
                    String objectName = f.join();
                    if (objectName != null) {
                        uploadedUrls.add(objectName);
                    }
                } catch (Exception e) {
                    if (execution.isCancelled()) break;
                    log.warn("图片处理异常: url={}", pageUrl, e);
                }
            }
            int uploaded = uploadedCounter.get();
            int skipped = skippedCounter.get();
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
    private void saveHtmlAndJs(Html doc, String url, String html, String title, TaskMessage msg,
                               SpiderTask task, boolean saveHtml, boolean saveResources,
                               Set<String> processedResourceUrls,
                               TaskExecutionContext execution,
                               java.util.concurrent.ExecutorService imageExecutor,
                               java.util.concurrent.Semaphore imageSemaphore) {
        if (execution.isCancelled()) return;
        try {
            String urlHash = ObjectNameUtils.hashUrl(url);
            if (saveHtml) {
                String htmlObject = "html/" + urlHash + ".html";
                minioHelper.putHtml(htmlBucket, htmlObject, html);
                saveFileMetadata(htmlBucket, htmlObject, html.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                        "text/html; charset=utf-8", "html", msg.getSpiderId(), title, url);
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
            // 并行下载 JS/CSS：虚拟线程池 + 信号量限流（与图片共享并发槽位）
            if (!resourceQueue.isEmpty()) {
                java.util.List<java.util.concurrent.CompletableFuture<Void>> resourceFutures = new java.util.ArrayList<>();
                for (WebResource resource : resourceQueue.values()) {
                    if (execution.isCancelled()) break;
                    imageSemaphore.acquireUninterruptibly();
                    if (execution.isCancelled()) {
                        imageSemaphore.release();
                        break;
                    }
                    final WebResource res = resource;
                    resourceFutures.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
                        try {
                            processWebResource(res, msg, task, title, execution);
                        } finally {
                            imageSemaphore.release();
                        }
                    }, imageExecutor));
                }
                for (java.util.concurrent.CompletableFuture<Void> f : resourceFutures) {
                    try {
                        f.join();
                    } catch (Exception e) {
                        if (!execution.isCancelled()) {
                            log.warn("JS/CSS 处理异常: url={}", url, e);
                        }
                    }
                }
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

    /**
     * 下载并上传 JS/CSS 资源。资源长度相同不代表内容相同，因此每次都以下载后的内容更新对象。
     */
    private void processWebResource(WebResource resource, TaskMessage msg, SpiderTask task, String title,
                                    TaskExecutionContext execution) {
        long start = System.currentTimeMillis();
        try {
            // 查询对象是否存在，用于日志区分首次上传和覆盖更新。
            boolean existingExists = false;
            try {
                minioHelper.statObject(jsBucket, resource.objectName());
                existingExists = true;
            } catch (io.minio.errors.ErrorResponseException e) {
                if (!("NoSuchKey".equals(e.errorResponse().code()) || "NoSuchBucket".equals(e.errorResponse().code()))) {
                    throw e;
                }
                // NoSuchKey/NoSuchBucket 表示对象不存在。
            }

            byte[] downloaded = downloadBytes(resource.url(), msg, execution);
            if (execution.isCancelled()) return;
            if (downloaded == null || downloaded.length == 0) {
                writeLog(task.getId(), msg.getSpiderId(), resource.url(), 0, "ERROR",
                        resource.category().toUpperCase() + " 下载为空", resourceCost(start), resource.category());
                return;
            }

            if ("js".equals(resource.category())) {
                minioHelper.putJs(jsBucket, resource.objectName(), downloaded);
            } else {
                minioHelper.putCss(jsBucket, resource.objectName(), downloaded);
            }
            saveOrUpdateResourceMetadata(resource, downloaded.length, msg.getSpiderId(), title);
            String message = existingExists ? "内容变化，覆盖更新" : "上传成功";
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
        return executeRequest(httpClient(msg), request, execution, msg, response -> {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            return response.body().bytes();
        });
    }

    private void saveFileMetadata(String bucket, String objectName, long fileSize, String contentType,
                                  String category, Long spiderId, String title, String source) {
        // 幂等插入：object_name 或 source 重复时自动忽略，避免多 worker 并发竞争
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
        fileMetadataMapper.insertIgnore(metadata);
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
        try {
            StatObjectResponse stat = minioHelper.statObject(imageBucket, objectName);
            // saveFileMetadata 内部用 INSERT IGNORE，已存在时自动跳过
            saveFileMetadata(imageBucket, objectName, stat.size(), stat.contentType(), inferCategory(objectName),
                    spiderId, title, source);
        } catch (Exception e) {
            log.warn("读取已有文件元数据失败: bucket={}, object={}", imageBucket, objectName, e);
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

    private Html fetch(String url, TaskMessage msg, TaskExecutionContext execution) throws Exception {
        // 自定义 Header 由 httpClient(msg) 上的 Interceptor 统一注入（含重定向跳数），此处只设内置 Header
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CollectX/1.0")
                .header("Accept", "text/html,application/xhtml+xml")
                .build();
        return executeRequest(httpClient(msg), request, execution, msg, response -> {
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
                                 TaskExecutionContext execution, TaskMessage msg,
                                 ResponseReader<T> reader) throws Exception {
        Call call = client.newCall(request);
        // 任务级超时：msg.timeout 单位是毫秒（前端 el-input-number min=1000 max=60000）。
        // 取 max(任务超时, 连接+读取超时) 作为 OkHttp call 整体超时，
        // 保证即使任务超时设得比底层超时还短，也不会因为 connect 阶段就炸掉。
        if (msg.getTimeout() != null && msg.getTimeout() > 0) {
            long minMs = (httpConnectTimeoutSeconds + httpReadTimeoutSeconds) * 1000L;
            call.timeout().timeout(Math.max(msg.getTimeout(), minMs), TimeUnit.MILLISECONDS);
        }
        execution.register(call);
        try {
            Response raw = call.execute();
            Response response = wrapWithSizeLimit(raw);
            try {
                return reader.read(response);
            } finally {
                response.close();
            }
        } finally {
            execution.unregister(call);
        }
    }

    /**
     * 将响应体缓冲到大小上限以内；发现超限时失败，禁止按截断内容解析页面。
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
        // Content-Length 未声明或 <= 上限时：读取时也检查实际大小，避免长度缺失或不准确导致超限。
        ResponseBody sourceBody = original.body();
        final long limit = httpMaxResponseBytes;
        final okio.BufferedSource src = sourceBody.source();
        final okio.Buffer buf;
        try {
            buf = readBodyWithinLimit(src, limit);
        } catch (java.io.IOException | RuntimeException e) {
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

    static okio.Buffer readBodyWithinLimit(okio.BufferedSource source, long limit) throws java.io.IOException {
        okio.Buffer buffer = new okio.Buffer();
        while (true) {
            long remaining = limit - buffer.size();
            if (remaining == 0) {
                if (source.request(1)) {
                    throw new IllegalStateException(
                            "HTTP 响应体超过大小上限 " + limit + " bytes，已丢弃");
                }
                return buffer;
            }
            long read = source.read(buffer, remaining);
            if (read == -1) {
                return buffer;
            }
        }
    }

    /** ThreadLocal MD5 实例：避免每页每资源都创建 MessageDigest（每页 2 次 + 每资源 1 次） */
    private static final ThreadLocal<java.security.MessageDigest> MD5_DIGEST =
            ThreadLocal.withInitial(() -> {
                try {
                    return java.security.MessageDigest.getInstance("MD5");
                } catch (java.security.NoSuchAlgorithmException e) {
                    throw new IllegalStateException("MD5 not available", e);
                }
            });

    private String md5(String input) {
        try {
            byte[] bytes = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] hash = MD5_DIGEST.get().digest(bytes);
            MD5_DIGEST.get().reset();
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
