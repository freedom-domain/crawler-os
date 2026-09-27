package com.collect.worker.crawler;

import org.junit.jupiter.api.Test;
import us.codecraft.webmagic.selector.Html;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentParserTest {

    @Test
    void parse_shouldExtractTitleSelectedContentAndAuthor() {
        var parsed = ContentParser.parse("""
                <html><head><title> Article title </title>
                <meta name="author" content="Jane Doe"></head>
                <body><main class="article">  Article <b>body</b> text </main></body></html>
                """, "https://example.com/article", ".article");

        assertEquals("Article title", parsed.getTitle());
        assertEquals("Article body text", parsed.getContent());
        assertEquals("Jane Doe", parsed.getAuthor());
    }

    @Test
    void isVipPage_shouldMatchSelectorWhenVipContentIsEmpty() {
        var doc = new Html("<div class='vip'></div>");

        assertTrue(ContentParser.isVipPage(doc, ".vip", ""));
    }

    @Test
    void isVipPage_shouldRequireVipContentToMatchInsideSelectedElement() {
        var doc = new Html("<div class='vip'>VIP会员专属内容</div><p>其他会员内容</p>");

        assertTrue(ContentParser.isVipPage(doc, ".vip", "会员专属"));
        assertFalse(ContentParser.isVipPage(doc, ".vip", "其他会员内容"));
        assertFalse(ContentParser.isVipPage(doc, ".missing", "会员专属"));
    }

    @Test
    void isVipPage_shouldRequireSelectorConfigurationAndMatch() {
        var doc = new Html("<div class='vip'>VIP会员专属内容</div>");

        assertFalse(ContentParser.isVipPage(doc, ".vip", "普通用户"));
        assertFalse(ContentParser.isVipPage(doc, "", ""));
        assertFalse(ContentParser.isVipPage(doc, null, "VIP"));
        assertFalse(ContentParser.isVipPage(null, ".vip", "VIP"));
    }
}
