package com.collect.worker.redis;

import com.collect.worker.crawler.ContentParser;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UrlQueueServiceTest {

    @Test
    void normalizeUrl_shouldDeduplicateEquivalentUrls() {
        String canonical = UrlQueueService.normalizeUrl("https://EXAMPLE.com:443/path?b=2&a=1#section");

        assertEquals("https://example.com/path?a=1&b=2", canonical);
        assertEquals(canonical, UrlQueueService.normalizeUrl("https://example.com/path?a=1&b=2"));
        assertEquals(canonical, UrlQueueService.normalizeUrl("https://example.com:443/path/?a=1&b=2#section"));
    }

    @Test
    void extractNextUrls_shouldDeduplicateEquivalentLinks() {
        String html = "<html><body>"
                + "<a href='https://example.com/path/?b=2&a=1#section'>A</a>"
                + "<a href='https://example.com/path?a=1&b=2'>B</a>"
                + "</body></html>";

        List<String> urls = ContentParser.extractNextUrls(org.jsoup.Jsoup.parse(html), "https://example.com", 1);

        assertEquals(1, urls.size());
        assertEquals("https://example.com/path?a=1&b=2", urls.get(0));
    }

    @Test
    void clear_shouldKeepVisitedUrlsForTaskIdempotency() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        UrlQueueService service = new UrlQueueService(redis);

        service.clear(42L);

        verify(redis).delete("crawler:urls:42");
        verify(redis).delete("crawler:urls:42:processing");
        verify(redis, never()).delete("crawler:urls:42:visited");
    }
}
