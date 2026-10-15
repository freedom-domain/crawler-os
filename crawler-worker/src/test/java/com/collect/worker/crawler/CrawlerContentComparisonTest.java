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
}
