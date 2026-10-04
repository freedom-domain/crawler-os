package com.collect.worker.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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

    private void push(Long taskId, String url) {
        String key = KEY_PREFIX + taskId;
        redis.opsForList().rightPush(key, url);
        redis.expire(key, TTL_HOURS, TimeUnit.HOURS);
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

    public boolean enqueueIfAbsent(Long taskId, String url, int depth) {
        if (isImageUrl(url)) {
            return false;
        }
        String normalized = normalizeUrl(url);
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        String key = KEY_PREFIX + taskId + ":visited";
        Long added = redis.opsForSet().add(key, normalized);
        redis.expire(key, TTL_HOURS, TimeUnit.HOURS);
        if (!Long.valueOf(1L).equals(added)) {
            return false;
        }
        push(taskId, normalized + "\t" + depth);
        return true;
    }

    /**
     * 原子认领待处理 URL，兼容旧队列中已经存在的重复项。
     */
    public boolean claimForProcessing(Long taskId, String url) {
        if (isImageUrl(url)) {
            return false;
        }
        String normalized = normalizeUrl(url);
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        String key = KEY_PREFIX + taskId + ":processing";
        Long added = redis.opsForSet().add(key, normalized);
        redis.expire(key, TTL_HOURS, TimeUnit.HOURS);
        return Long.valueOf(1L).equals(added);
    }
}
