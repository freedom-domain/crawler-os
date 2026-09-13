package com.collect.worker.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlQueueService {

    private final StringRedisTemplate redis;

    private static final String KEY_PREFIX = "crawler:urls:";
    private static final long TTL_HOURS = 24;

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
    }

    public boolean isVisited(Long taskId, String url) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(KEY_PREFIX + taskId + ":visited", url));
    }

    public void markVisited(Long taskId, String url) {
        String key = KEY_PREFIX + taskId + ":visited";
        redis.opsForSet().add(key, url);
        redis.expire(key, TTL_HOURS, TimeUnit.HOURS);
    }
}
