package com.collect.worker.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlQueueService {

    private final StringRedisTemplate redis;

    private static final String KEY_PREFIX = "crawler:urls:";
    private static final long TTL_HOURS = 24;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "apng", "avif", "bmp", "gif", "heic", "heif", "ico", "jfif",
            "jpe", "jpeg", "jpg", "pjp", "pjpeg", "png", "svg", "tif",
            "tiff", "webp"
    );

    /**
     * 入队脚本：SADD visited + EXPIRE + RPUSH queue + EXPIRE，合并为 1 次 RTT。
     * 返回 1=入队成功，0=已存在（跳过）。
     */
    private static final RedisScript<Long> ENQUEUE_SCRIPT = new DefaultRedisScript<>(
            "local added = redis.call('SADD', KEYS[1], ARGV[1]) " +
            "if added == 0 then return 0 end " +
            "redis.call('EXPIRE', KEYS[1], ARGV[2]) " +
            "redis.call('RPUSH', KEYS[2], ARGV[3]) " +
            "redis.call('EXPIRE', KEYS[2], ARGV[2]) " +
            "return 1", Long.class);

    private static final RedisScript<Long> BATCH_ENQUEUE_SCRIPT = new DefaultRedisScript<>(
            "local addedCount = 0 " +
            "for i = 2, #ARGV, 2 do " +
            "local added = redis.call('SADD', KEYS[1], ARGV[i]) " +
            "if added == 1 then " +
            "redis.call('RPUSH', KEYS[2], ARGV[i] .. '\\t' .. ARGV[i + 1]) " +
            "addedCount = addedCount + 1 " +
            "end " +
            "end " +
            "if addedCount > 0 then " +
            "redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
            "redis.call('EXPIRE', KEYS[2], ARGV[1]) " +
            "end " +
            "return addedCount", Long.class);
    private static final int BATCH_SIZE = 500;

    /**
     * 原子弹出并认领一个 URL，避免 LPOP 与 SADD 间的网络往返及重复消费竞态。
     * 队列中已被认领的旧项目会在脚本内跳过。
     */
    private static final RedisScript<String> POP_AND_CLAIM_SCRIPT = new DefaultRedisScript<>(
            "while true do " +
            "local item = redis.call('LPOP', KEYS[1]) " +
            "if not item then return nil end " +
            "local separator = string.find(item, '\\t', 1, true) " +
            "local url = item " +
            "if separator then url = string.sub(item, 1, separator - 1) end " +
            "local added = redis.call('SADD', KEYS[2], url) " +
            "if added == 1 then " +
            "redis.call('EXPIRE', KEYS[2], ARGV[1]) " +
            "return item " +
            "end " +
            "end", String.class);

    public static String normalizeUrl(String rawUrl) {
        if (rawUrl == null) {
            return null;
        }
        String url = rawUrl.trim();
        if (url.isEmpty()) {
            return url;
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.isEmpty()) {
                return null;
            }
            int port = uri.getPort();
            if ("http".equals(scheme) && port == 80) {
                port = -1;
            }
            if ("https".equals(scheme) && port == 443) {
                port = -1;
            }
            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            } else if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            String query = normalizeQuery(uri.getRawQuery());
            StringBuilder builder = new StringBuilder();
            if (!scheme.isEmpty()) {
                builder.append(scheme).append("://");
            }
            if (!host.isEmpty()) {
                builder.append(host);
                if (port != -1) {
                    builder.append(':').append(port);
                }
            }
            builder.append(path);
            if (!query.isEmpty()) {
                builder.append('?').append(query);
            }
            return builder.toString();
        } catch (Exception e) {
            return url;
        }
    }

    public static boolean isImageUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return false;
        }
        try {
            String path = URI.create(rawUrl.trim()).getPath();
            if (path == null || path.isEmpty()) {
                return false;
            }
            int slash = path.lastIndexOf('/');
            int dot = path.lastIndexOf('.');
            return dot > slash && IMAGE_EXTENSIONS.contains(path.substring(dot + 1).toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String normalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        List<String> params = new ArrayList<>();
        for (String param : rawQuery.split("&")) {
            if (param == null || param.isBlank()) {
                continue;
            }
            params.add(param);
        }
        params.sort(Comparator.naturalOrder());
        return String.join("&", params);
    }

    public String popAndClaim(Long taskId) {
        String queueKey = KEY_PREFIX + taskId;
        String processingKey = queueKey + ":processing";
        return redis.execute(POP_AND_CLAIM_SCRIPT,
                List.of(queueKey, processingKey),
                String.valueOf(TTL_HOURS * 3600));
    }

    public long size(Long taskId) {
        String key = KEY_PREFIX + taskId;
        Long s = redis.opsForList().size(key);
        return s != null ? s : 0;
    }

    public void clear(Long taskId) {
        redis.delete(KEY_PREFIX + taskId);
        redis.delete(KEY_PREFIX + taskId + ":processing");
    }

    /**
     * 原子入队：Lua 脚本合并 SADD + EXPIRE + RPUSH + EXPIRE 为 1 次 RTT。
     * 原实现需 4 次 RTT，优化后 1 次。
     * 使用内存缓存避免重复 normalizeUrl 解析。
     */
    public boolean enqueueIfAbsent(Long taskId, String url, int depth) {
        if (isImageUrl(url)) {
            return false;
        }
        String normalized = normalizeUrlCached(url);
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        String visitedKey = KEY_PREFIX + taskId + ":visited";
        String queueKey = KEY_PREFIX + taskId;
        Long result = redis.execute(ENQUEUE_SCRIPT,
                List.of(visitedKey, queueKey),
                normalized, String.valueOf(TTL_HOURS * 3600), normalized + "\t" + depth);
        return Long.valueOf(1L).equals(result);
    }

    /**
     * Atomically deduplicates and enqueues discovered URLs in bounded batches to reduce Redis RTTs.
     * The batch size limits command payload size, not the number of URLs accepted.
     */
    public int enqueueAllIfAbsent(Long taskId, List<UrlToEnqueue> urls) {
        if (urls == null || urls.isEmpty()) {
            return 0;
        }
        String visitedKey = KEY_PREFIX + taskId + ":visited";
        String queueKey = KEY_PREFIX + taskId;
        int enqueued = 0;
        List<String> arguments = new ArrayList<>(BATCH_SIZE * 2 + 1);
        arguments.add(String.valueOf(TTL_HOURS * 3600));
        for (UrlToEnqueue entry : urls) {
            if (entry == null || isImageUrl(entry.url())) {
                continue;
            }
            String normalized = normalizeUrlCached(entry.url());
            if (normalized == null || normalized.isBlank()) {
                continue;
            }
            arguments.add(normalized);
            arguments.add(String.valueOf(entry.depth()));
            if (arguments.size() >= BATCH_SIZE * 2 + 1) {
                enqueued += executeBatchEnqueue(visitedKey, queueKey, arguments);
                arguments = new ArrayList<>(BATCH_SIZE * 2 + 1);
                arguments.add(String.valueOf(TTL_HOURS * 3600));
            }
        }
        if (arguments.size() > 1) {
            enqueued += executeBatchEnqueue(visitedKey, queueKey, arguments);
        }
        return enqueued;
    }

    private int executeBatchEnqueue(String visitedKey, String queueKey, List<String> arguments) {
        Long result = redis.execute(BATCH_ENQUEUE_SCRIPT, List.of(visitedKey, queueKey),
                arguments.toArray());
        return result == null ? 0 : result.intValue();
    }

    public record UrlToEnqueue(String url, int depth) {}

    /**
     * 原子认领：Lua 脚本合并 SADD + EXPIRE 为 1 次 RTT。
     * 原实现需 2 次 RTT，优化后 1 次。
     * 使用内存缓存避免重复 normalizeUrl 解析。
     */
    /**
     * 归一化缓存：线程安全 LRU，上限 100k 条（~20MB 峰值），淘汰最久未访问的 URL。
     * 原上限 500k（~100MB）对单任务 URL 量过剩；100k 足以覆盖绝大多数站点，
     * 淘汰后重新 normalize 即可，不影响正确性。
     */
    private final Map<String, String> normalizeCache = buildLruCache(100_000);

    /**
     * 构建线程安全的 LRU 缓存：底层 LinkedHashMap（accessOrder=true）
     * 包装在 Collections.synchronizedMap 中，容量超出时自动淘汰最久未访问的条目。
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

    /**
     * 带内存缓存的 URL 归一化：同一 URL 多次调用只解析一次。
     * {@link #normalizeUrl} 是纯函数（相同输入 → 相同输出），缓存安全。
     * LRU 自动淘汰，无需手动 clear。
     */
    private String normalizeUrlCached(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        String cached = normalizeCache.get(url);
        if (cached != null) {
            return cached;
        }
        String result = normalizeUrl(url);
        if (result != null) {
            normalizeCache.put(url, result);
        }
        return result;
    }
}
