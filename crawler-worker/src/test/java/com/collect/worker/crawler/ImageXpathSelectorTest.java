package com.collect.worker.crawler;

import org.junit.jupiter.api.Test;
import us.codecraft.webmagic.selector.Html;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageXpathSelectorTest {

    @Test
    void imageExtensionShouldUseContentSignatureWhenUrlHasNoExtension() {
        byte[] pngHeader = {(byte) 0x89, 'P', 'N', 'G'};

        assertEquals(".img", CrawlerEngine.guessExt("https://example.com/image?id=1", null));
        assertEquals(".png", CrawlerEngine.guessExt("https://example.com/image?id=1", pngHeader));
        assertEquals(".png", CrawlerEngine.guessExt("https://example.com/image.jpg", pngHeader));
    }

    @Test
    void resourceExtensionShouldOnlyUseFinalPathSegment() {
        assertEquals("", CrawlerEngine.extensionFromUrl("https://example.com/file.js/route"));
        assertEquals(".js", CrawlerEngine.extensionFromUrl("https://example.com/file.js?version=2"));
    }

    @Test
    void shouldExtractSelectorImagesDirectlyWhenXpathIsEmpty() {
        var document = new Html("""
                <div class="selected"><img src="/inside.jpg"></div>
                <div class="outside"><img src="/outside.jpg"></div>
                """, "https://example.com/page");

        var sources = CrawlerEngine.extractImageSources(
                document, "https://example.com/page", ".selected", "");

        assertEquals(java.util.List.of("https://example.com/inside.jpg"), sources);
    }

    @Test
    void xpathShouldApplyToWholeDocumentWhenSelectorIsEmpty() {
        var document = new Html("""
                <div class="selected"><img src="/inside.jpg"></div>
                <div class="outside"><img src="/outside.jpg"></div>
                """, "https://example.com/page");

        var xpathOnly = CrawlerEngine.extractImageSources(
                document, "https://example.com/page", "", "//img/@src");

        assertEquals(java.util.List.of(
                "https://example.com/inside.jpg",
                "https://example.com/outside.jpg"), xpathOnly);
    }

    @Test
    void shouldRequireSelectorMatchBeforeApplyingXpath() {
        var document = new Html("""
                <div class="selected"><img src="/inside.jpg"></div>
                <div class="outside"><img src="/outside.jpg"></div>
                """, "https://example.com/page");

        var withoutSelectorMatch = CrawlerEngine.extractImageSources(
                document, "https://example.com/page", ".missing", "//img/@src");
        var withSelectorAndXpath = CrawlerEngine.extractImageSources(
                document, "https://example.com/page", ".selected", "//img/@src");

        assertEquals(java.util.List.of(), withoutSelectorMatch);
        assertEquals(java.util.List.of("https://example.com/inside.jpg"), withSelectorAndXpath);
    }

    @Test
    void shouldReturnEmptyWhenNeitherSelectorNorXpathConfigured() {
        var document = new Html(
                "<div class=\"selected\"><img src=\"/inside.jpg\"></div>", "https://example.com/page");

        var sources = CrawlerEngine.extractImageSources(
                document, "https://example.com/page", "", "");

        assertEquals(java.util.List.of(), sources);
    }

    @Test
    void xpathShouldOnlyMatchInsideCssSelectedElement() {
        var document = new Html("""
                <div class="selected"><img src="/inside.jpg"></div>
                <div class="outside"><img src="/outside.jpg"></div>
                """, "https://example.com/page");

        var sources = CrawlerEngine.extractImageSourcesByXpath(
                document.$(".selected").nodes().get(0), "https://example.com/page", "//img/@src");

        assertEquals(java.util.List.of("https://example.com/inside.jpg"), sources);
    }

    @Test
    void xpathShouldMatchCssSelectedImageElementItself() {
        var document = new Html(
                "<img class='selected' src='/image.jpg'>", "https://example.com/page");

        var sources = CrawlerEngine.extractImageSourcesByXpath(
                document.$(".selected").nodes().get(0), "https://example.com/page", "//img/@src");

        assertEquals(java.util.List.of("https://example.com/image.jpg"), sources);
    }

    @Test
    void xpathShouldExtractSrcWhenItSelectsImageNodes() {
        var document = new Html("""
                <div class="selected"><img src="/inside.jpg"></div>
                <div class="outside"><img src="/outside.jpg"></div>
                """, "https://example.com/page");

        var sources = CrawlerEngine.extractImageSources(
                document, "https://example.com/page", ".selected", "//img");

        assertEquals(java.util.List.of("https://example.com/inside.jpg"), sources);
    }

    @Test
    void shouldCombineSemicolonSeparatedXpathsAndIgnoreEmptyExpressions() {
        var document = new Html("""
                <div class="selected"><img src="/first.jpg"></div>
                <div class="outside"><img src="/second.jpg"></div>
                """, "https://example.com/page");

        var sources = CrawlerEngine.extractImageSources(
                document, "https://example.com/page", "",
                " //div[@class='selected']//img/@src ; ; //div[@class='outside']//img/@src; ");

        assertEquals(java.util.List.of(
                "https://example.com/first.jpg",
                "https://example.com/second.jpg"), sources);
    }

    @Test
    void shouldCombineSemicolonSeparatedXpathsWithinCssSelectedScope() {
        var document = new Html("""
                <div class="selected">
                  <img src="/first.jpg">
                  <img data-src="/second.jpg">
                </div>
                <div class="outside"><img src="/outside.jpg"></div>
                """, "https://example.com/page");

        var sources = CrawlerEngine.extractImageSources(
                document, "https://example.com/page", ".selected",
                "//img/@src; //img/@data-src");

        assertEquals(java.util.List.of(
                "https://example.com/first.jpg",
                "https://example.com/second.jpg"), sources);
    }
}
