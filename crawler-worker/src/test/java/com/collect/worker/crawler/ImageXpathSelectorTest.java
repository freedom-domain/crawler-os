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

    @Test
    void noscriptImagesShouldBeExtractedWhenNoDomMatch() {
        String rawHtml = """
                <html><body>
                <noscript><img decoding="async" width="1080" height="1401"
                  src="https://www.61ok.com/imgs/2024/02/2024021104510128.png"
                  alt="test" class="wp-image-10891"/></noscript>
                </body></html>
                """;
        var document = new Html(rawHtml, "https://www.61ok.com/page");

        // 选择器匹配不到 noscript 内容 → 回退到正则提取
        var sources = CrawlerEngine.extractImageSources(
                document, "https://www.61ok.com/page", ".content", "");

        assertEquals(java.util.List.of(
                "https://www.61ok.com/imgs/2024/02/2024021104510128.png"), sources);
    }

    @Test
    void noscriptImagesShouldBeExtractedWhenXpathFindsNothing() {
        String rawHtml = """
                <html><body>
                <div class="content">no images here</div>
                <noscript><img src="/lazy.jpg"/></noscript>
                </body></html>
                """;
        var document = new Html(rawHtml, "https://example.com/page");

        // XPath 匹配不到 img → 回退到 noscript 正则
        var sources = CrawlerEngine.extractImageSources(
                document, "https://example.com/page", ".content", "//img/@src");

        assertEquals(java.util.List.of("https://example.com/lazy.jpg"), sources);
    }

    @Test
    void noscriptImagesShouldNotBeExtractedWhenDomHasImages() {
        String rawHtml = """
                <html><body>
                <div class="content"><img src="/dom.jpg"/></div>
                <noscript><img src="/noscript.jpg"/></noscript>
                </body></html>
                """;
        var document = new Html(rawHtml, "https://example.com/page");

        // DOM 已有图片 → noscript 是 JS 禁用时的回退，不重复提取
        var sources = CrawlerEngine.extractImageSources(
                document, "https://example.com/page", ".content", "");

        assertEquals(java.util.List.of("https://example.com/dom.jpg"), sources);
    }

    @Test
    void extractNoscriptImageSourcesShouldHandleRelativeAndAbsoluteUrls() {
        String rawHtml = """
                <noscript><img src="/relative.png"/><img src="https://cdn.example.com/abs.jpg"/></noscript>
                """;
        var sources = CrawlerEngine.extractNoscriptImageSources(rawHtml, "https://example.com/page");
        assertEquals(java.util.List.of(
                "https://example.com/relative.png",
                "https://cdn.example.com/abs.jpg"), sources);
    }

    @Test
    void extractNoscriptImageSourcesShouldReturnEmptyForNullOrNoNoscript() {
        assertEquals(java.util.List.of(), CrawlerEngine.extractNoscriptImageSources(null, "https://x.com"));
        assertEquals(java.util.List.of(), CrawlerEngine.extractNoscriptImageSources("", "https://x.com"));
        assertEquals(java.util.List.of(), CrawlerEngine.extractNoscriptImageSources(
                "<div>no noscript here</div>", "https://x.com"));
    }
}
