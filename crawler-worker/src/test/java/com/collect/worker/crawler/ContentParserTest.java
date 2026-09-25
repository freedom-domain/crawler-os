package com.collect.worker.crawler;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentParserTest {

    @Test
    void matchesSelectorContent_shouldMatchContainedText() {
        var doc = Jsoup.parse("<div class='vip'>VIP会员专属内容</div>");

        assertTrue(ContentParser.matchesSelectorContent(doc, ".vip", "会员专属"));
        assertFalse(ContentParser.matchesSelectorContent(doc, ".vip", "普通用户"));
        assertFalse(ContentParser.matchesSelectorContent(doc, ".missing", "会员"));
    }

    @Test
    void matchesSelectorContent_shouldNotMatchWhenConfigurationIsIncomplete() {
        var doc = Jsoup.parse("<div class='vip'>VIP会员专属内容</div>");

        assertFalse(ContentParser.matchesSelectorContent(doc, ".vip", ""));
        assertFalse(ContentParser.matchesSelectorContent(doc, "", "会员"));
    }
}
