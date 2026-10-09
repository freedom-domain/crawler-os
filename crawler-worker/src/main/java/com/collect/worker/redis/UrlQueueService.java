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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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

    /**
     * 认领脚本：SADD processing + EXPIRE，合并为 1 次 RTT。
     * 返回 1=认领成功，0=已被其他 worker 认领。
     */
    private static final RedisScript<Long> CLAIM_SCRIPT = new DefaultRedisScript<>(
            "local added = redis.call('SADD', KEYS[1], ARGV[1]) " +
            "if added == 0 then return 0 end " +
            "redis.call('EXPIRE', KEYS[1], ARGV[2]) " +
            "return 1", Long.class);

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

    public String pop(Long taskId) {
        String key = KEY_PREFIX + taskId;
        return redis.opsForList().leftPop(key);
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
     * 原子认领：Lua 脚本合并 SADD + EXPIRE 为 1 次 RTT。
     * 原实现需 2 次 RTT，优化后 1 次。
     * 使用内存缓存避免重复 normalizeUrl 解析。
     */
    private final Map<String, String> normalizeCache = new ConcurrentHashMap<>();

    /** 归一化缓存上限：防止无限增长（每个任务约 10k URL，20 任务 = 200k，取 500k 安全值） */
    private static final int NORMALIZE_CACHE_MAX = 500_000;

    /**
     * 带内存缓存的 URL 归一化：同一 URL 多次调用只解析一次。
     * {@link #normalizeUrl} 是纯函数（相同输入 → 相同输出），缓存安全。
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
            if (normalizeCache.size() < NORMALIZE_CACHE_MAX) {
                normalizeCache.put(url, result);
            } else {
                // 缓存满了，清空重建（简单粗暴但安全，避免锁竞争）
                normalizeCache.clear();
                normalizeCache.put(url, result);
            }
        }
        return result;
    }

    public boolean claimForProcessing(Long taskId, String url) {
        if (isImageUrl(url)) {
            return false;
        }
        String normalized = normalizeUrlCached(url);
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        String processingKey = KEY_PREFIX + taskId + ":processing";
        Long result = redis.execute(CLAIM_SCRIPT,
                List.of(processingKey),
                normalized, String.valueOf(TTL_HOURS * 3600));
        return Long.valueOf(1L).equals(result);
    }
}
