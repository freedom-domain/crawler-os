package com.collect.worker.crawler;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

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
    void xpathShouldOnlyMatchInsideCssSelectedElement() throws Exception {
        var document = Jsoup.parse("""
                <div class="selected"><img src="/inside.jpg"></div>
                <div class="outside"><img src="/outside.jpg"></div>
                """, "https://example.com/page");

        var sources = CrawlerEngine.extractImageSourcesByXpath(
                document.selectFirst(".selected"), "https://example.com/page", "//img");

        assertEquals(java.util.List.of("https://example.com/inside.jpg"), sources);
    }

    @Test
    void xpathShouldMatchCssSelectedImageElementItself() throws Exception {
        var document = Jsoup.parse(
                "<img class='selected' src='/image.jpg'>", "https://example.com/page");

        var sources = CrawlerEngine.extractImageSourcesByXpath(
                document.selectFirst(".selected"), "https://example.com/page", "//img");

        assertEquals(java.util.List.of("https://example.com/image.jpg"), sources);
    }
}
