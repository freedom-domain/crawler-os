package com.collect.worker.crawler;

import lombok.Data;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
public class ContentParser {

    private String url;
    private String title;
    private String content;
    private String author;
    private List<String> nextUrls = new ArrayList<>();

    public static ContentParser parse(String html, String baseUrl) {
        return parse(html, baseUrl, null);
    }

    /**
     * 解析页面内容。
     * 若配置了内容选择器（contentSelector），优先取选择器命中元素的文本作为内容；
     * 若选择器未命中或文本为空，则回退到标题作为内容。
     * 未配置内容选择器时，保持原有行为（取 body 文本）。
     */
    public static ContentParser parse(String html, String baseUrl, String contentSelector) {
        Document doc = Jsoup.parse(html, baseUrl);
        ContentParser parser = new ContentParser();
        parser.setUrl(baseUrl);

        String title = doc.title() != null ? doc.title().trim() : baseUrl;
        parser.setTitle(title);

        String content;
        if (contentSelector != null && !contentSelector.isBlank()) {
            Element contentEl = doc.selectFirst(contentSelector);
            String selectedText = contentEl != null ? contentEl.text().trim() : "";
            if (!selectedText.isEmpty()) {
                content = selectedText.length() > 5000 ? selectedText.substring(0, 5000) : selectedText;
            } else {
                // 内容选择器无内容，回退到标题
                content = title;
            }
        } else {
            Element body = doc.body();
            String text = body != null ? body.text() : "";
            content = text.length() > 5000 ? text.substring(0, 5000) : text;
        }
        parser.setContent(content);

        Element authorEl = doc.selectFirst("meta[name='author']");
        if (authorEl != null) {
            parser.setAuthor(authorEl.attr("content"));
        } else {
            authorEl = doc.selectFirst(".author, .byline, [rel='author']");
            parser.setAuthor(authorEl != null ? authorEl.text() : "");
        }

        return parser;
    }

    public static List<String> extractNextUrls(Document doc, String baseUrl, int remainingDepth) {
        List<String> urls = new ArrayList<>();
        if (remainingDepth <= 0) {
            return urls;
        }
        // 每页链接无上限，仅做页内去重与协议过滤；同一任务内还会在队列层统一规范化再次去重
        Set<String> seen = new HashSet<>();
        for (Element a : doc.select("a[href]")) {
            String href = a.absUrl("href");
            if (href == null || href.isBlank()) {
                continue;
            }
            String normalizedHref = com.collect.worker.redis.UrlQueueService.normalizeUrl(href);
            if (normalizedHref == null || normalizedHref.isBlank()
                    || !seen.add(normalizedHref)) {
                continue;
            }
            urls.add(normalizedHref);
        }
        return urls;
    }
}
