package com.collect.worker.crawler;

import okhttp3.Request;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrawlerHeaderConfigurationTest {

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CollectX/1.0";

    private List<Map.Entry<String, String>> entries(String headers) {
        return CrawlerEngine.parseConfiguredHeaders(headers);
    }

    private String valueOf(List<Map.Entry<String, String>> list, String name) {
        return list.stream().filter(e -> e.getKey().equals(name)).map(Map.Entry::getValue).findFirst().orElse(null);
    }

    @Test
    void nullOrBlankHeaders_shouldReturnEmpty() {
        assertTrue(entries(null).isEmpty());
        assertTrue(entries("").isEmpty());
        assertTrue(entries("   \n  ").isEmpty());
    }

    @Test
    void shouldParseSingleHeader() {
        List<Map.Entry<String, String>> list = entries("X-Api-Key: abc123");
        assertEquals(1, list.size());
        assertEquals("abc123", valueOf(list, "X-Api-Key"));
    }

    @Test
    void shouldParseMultipleHeadersWithCommentsAndBlankLines() {
        List<Map.Entry<String, String>> list = entries(
                "# 认证\nX-Api-Key: abc123\n\nX-Auth-Token: tok-1 # 行内注释\n");
        assertEquals(2, list.size());
        assertEquals("abc123", valueOf(list, "X-Api-Key"));
        assertEquals("tok-1", valueOf(list, "X-Auth-Token"));
    }

    @Test
    void invalidLines_shouldBeIgnored() {
        List<Map.Entry<String, String>> list = entries(
                "no-colon-line\n: no-name\nEmpty-Value: \n# only comment");
        assertTrue(list.isEmpty());
    }

    @Test
    void applyConfiguredHeaders_shouldOverrideBuiltInDefault() {
        Request request = new Request.Builder()
                .url("https://example.com/a")
                .header("User-Agent", UA)
                .build();
        Request result = CrawlerEngine.applyConfiguredHeaders(request, "User-Agent: MyBot/2.0");
        assertEquals("MyBot/2.0", result.header("User-Agent"));
    }

    @Test
    void applyConfiguredHeaders_shouldReturnSameRequestWhenNoHeaders() {
        Request request = new Request.Builder()
                .url("https://example.com/a")
                .header("User-Agent", UA)
                .build();
        assertEquals(request, CrawlerEngine.applyConfiguredHeaders(request, null));
        assertEquals(request, CrawlerEngine.applyConfiguredHeaders(request, "  "));
    }
}
