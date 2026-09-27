package com.collect.worker.crawler;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentParserTest {

    @Test
    void isVipPage_shouldMatchSelectorWhenVipContentIsEmpty() {
        var doc = Jsoup.parse("<div class='vip'></div>");

        assertTrue(ContentParser.isVipPage(doc, ".vip", ""));
    }

    @Test
    void isVipPage_shouldRequireVipContentToMatchInsideSelectedElement() {
        var doc = Jsoup.parse("<div class='vip'>VIP会员专属内容</div><p>其他会员内容</p>");

        assertTrue(ContentParser.isVipPage(doc, ".vip", "会员专属"));
        assertFalse(ContentParser.isVipPage(doc, ".vip", "其他会员内容"));
        assertFalse(ContentParser.isVipPage(doc, ".missing", "会员专属"));
    }

    @Test
    void isVipPage_shouldRequireSelectorConfigurationAndMatch() {
        var doc = Jsoup.parse("<div class='vip'>VIP会员专属内容</div>");

        assertFalse(ContentParser.isVipPage(doc, ".vip", "普通用户"));
        assertFalse(ContentParser.isVipPage(doc, "", ""));
        assertFalse(ContentParser.isVipPage(doc, null, "VIP"));
        assertFalse(ContentParser.isVipPage(null, ".vip", "VIP"));
    }
}
