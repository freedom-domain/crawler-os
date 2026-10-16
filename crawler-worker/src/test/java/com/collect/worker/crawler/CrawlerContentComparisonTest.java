package com.collect.worker.crawler;

import com.collect.worker.es.SpiderContentDoc;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrawlerContentComparisonTest {

    @Test
    void hasSameContentAndImages_shouldMatchBothFields() {
        SpiderContentDoc existing = new SpiderContentDoc();
        existing.setContent("article body");
        existing.setImages(List.of("images/first.jpg", "images/second.png"));

        assertTrue(CrawlerEngine.hasSameContentAndImages(
                existing, "article body", List.of("images/first.jpg", "images/second.png")));
        assertFalse(CrawlerEngine.hasSameContentAndImages(
                existing, "updated body", List.of("images/first.jpg", "images/second.png")));
        assertFalse(CrawlerEngine.hasSameContentAndImages(
                existing, "article body", List.of("images/first.jpg")));
        assertFalse(CrawlerEngine.hasSameContentAndImages(null, "article body", List.of()));
    }

    @Test
    void hasSameContentAndImages_shouldTreatNullImagesAsEmpty() {
        SpiderContentDoc existing = new SpiderContentDoc();
        existing.setContent("article body");

        assertTrue(CrawlerEngine.hasSameContentAndImages(existing, "article body", List.of()));
    }

    @Test
    void shouldSkipUnchangedContent_shouldNotCompareWhenForceOverwriting() {
        SpiderContentDoc existing = new SpiderContentDoc();
        existing.setContent("article body");
        existing.setImages(List.of("images/first.jpg"));

        assertFalse(CrawlerEngine.shouldSkipUnchangedContent(
                existing, "article body", List.of("images/first.jpg"), true));
        assertTrue(CrawlerEngine.shouldSkipUnchangedContent(
                existing, "article body", List.of("images/first.jpg"), false));
    }

    @Test
    void hasSameBytes_shouldIdentifyUnchangedAndChangedMinioContent() {
        assertTrue(CrawlerEngine.hasSameBytes(new byte[] {1, 2}, new byte[] {1, 2}));
        assertFalse(CrawlerEngine.hasSameBytes(new byte[] {1, 2}, new byte[] {1, 3}));
        assertFalse(CrawlerEngine.hasSameBytes(null, new byte[] {1, 2}));
    }

    @Test
    void shouldReadCachedHtml_forAnyPageWhenNotOverwriting() {
        assertTrue(CrawlerEngine.shouldReadCachedHtml(false, false, false));
        assertFalse(CrawlerEngine.shouldReadCachedHtml(false, true, false));
        assertTrue(CrawlerEngine.shouldReadCachedHtml(true, false, false));
        assertFalse(CrawlerEngine.shouldReadCachedHtml(true, true, true));
    }

    @Test
    void shouldSkipCachedDiscoveredUrl_whenEitherCacheHasEntryAndNotOverwriting() {
        assertTrue(CrawlerEngine.shouldSkipCachedDiscoveredUrl(true, false, false, false));
        assertTrue(CrawlerEngine.shouldSkipCachedDiscoveredUrl(false, true, false, false));
        assertFalse(CrawlerEngine.shouldSkipCachedDiscoveredUrl(false, false, false, false));
        assertFalse(CrawlerEngine.shouldSkipCachedDiscoveredUrl(true, false, true, false));
        assertFalse(CrawlerEngine.shouldSkipCachedDiscoveredUrl(true, false, false, true));
    }
}
