package com.collect.worker.crawler;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebResourceQueueTest {

    @Test
    void shouldEnqueueEachResourceUrlOnlyOnce() {
        var queue = new LinkedHashMap<String, CrawlerEngine.WebResource>();
        var processedUrls = new HashSet<String>();
        var script = new CrawlerEngine.WebResource(
                "https://example.com/shared", "https://example.com/page", "js",
                "js/object.js", "application/javascript");
        var stylesheet = new CrawlerEngine.WebResource(
                "https://example.com/shared", "https://example.com/page", "css",
                "css/object.css", "text/css");

        assertTrue(CrawlerEngine.enqueueResource(queue, processedUrls, script));
        assertFalse(CrawlerEngine.enqueueResource(queue, processedUrls, stylesheet));

        assertEquals(1, queue.size());
        assertEquals("js", queue.get("https://example.com/shared").category());
        assertEquals("https://example.com/page", queue.get("https://example.com/shared").referrer());
    }
}
