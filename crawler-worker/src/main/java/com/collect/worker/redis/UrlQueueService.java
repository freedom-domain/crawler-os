package com.collect.worker.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlQueueService {

    private final StringRedisTemplate redis;

    private static final String KEY_PREFIX = "crawler:urls:";
    private static final long TTL_HOURS = 24;

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
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
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

    private static String normalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        List<String> params = new ArrayList<>();
        for (String param : rawQuery.split("&")) {
            if (param == null || param.isBlank()) {
                continue;
            }
            String decodedParam = param;
            int idx = param.indexOf('=');
            if (idx >= 0) {
                String key = param.substring(0, idx);
                String value = param.substring(idx + 1);
                decodedParam = safeDecode(key) + "=" + safeDecode(value);
            } else {
                decodedParam = safeDecode(param);
            }
            params.add(decodedParam);
        }
        params.sort(Comparator.naturalOrder());
        return String.join("&", params);
    }

    private static String safeDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    public void push(Long taskId, String url) {
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
        redis.delete(KEY_PREFIX + taskId + ":visited");
    }

    public boolean isVisited(Long taskId, String url) {
        String normalized = normalizeUrl(url);
        return normalized == null || Boolean.TRUE.equals(redis.opsForSet().isMember(KEY_PREFIX + taskId + ":visited", normalized));
    }

    public void markVisited(Long taskId, String url) {
        String normalized = normalizeUrl(url);
        if (normalized == null) {
            return;
        }
        String key = KEY_PREFIX + taskId + ":visited";
        redis.opsForSet().add(key, normalized);
        redis.expire(key, TTL_HOURS, TimeUnit.HOURS);
    }
}
