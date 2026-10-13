package com.collect.worker.crawler;

import lombok.Data;
import us.codecraft.webmagic.selector.Html;
import us.codecraft.webmagic.selector.HtmlNode;
import us.codecraft.webmagic.selector.Selectable;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
public class ContentParser {

    private String url;
    private String title;
    private String content;
    private String author;
    private List<String> nextUrls = new ArrayList<>();

    /**
     * 解析页面内容（无内容选择器，取 body 文本）。
     */
    public static ContentParser parse(String html, String baseUrl) {
        return parse(html, baseUrl, null);
    }

    /**
     * 解析页面内容。
     * 若配置了内容选择器（contentSelector），优先取选择器命中元素的文本作为内容；
     * 若选择器未命中或文本为空，则回退到标题作为内容。
     * 未配置内容选择器时，保持原有行为（取 body 文本）。
     */
    public static ContentParser parse(String source, String baseUrl, String contentSelector) {
        return parse(new Html(source, baseUrl), baseUrl, contentSelector);
    }

    /**
     * 解析页面内容（复用已解析的 {@link Html}，避免重复 DOM 解析）。
     */
    public static ContentParser parse(Html doc, String baseUrl, String contentSelector) {
        ContentParser parser = new ContentParser();
        parser.setUrl(baseUrl);

        List<Selectable> titleElements = doc.$("title").nodes();
        String title = titleElements.isEmpty() ? "" : textOf(titleElements.get(0));
        if (title.isEmpty()) {
            title = baseUrl;
        }
        parser.setTitle(title);

        String content;
        if (contentSelector != null && !contentSelector.isBlank()) {
            List<Selectable> matched = doc.$(contentSelector).nodes();
            String selectedText = matched.isEmpty() ? "" : textOf(matched.get(0));
            if (!selectedText.isEmpty()) {
                content = selectedText.length() > 5000 ? selectedText.substring(0, 5000) : selectedText;
            } else {
                content = title;
            }
        } else {
            List<Selectable> bodyElements = doc.$("body").nodes();
            String text = bodyElements.isEmpty() ? "" : textOf(bodyElements.get(0));
            content = text.length() > 5000 ? text.substring(0, 5000) : text;
        }
        parser.setContent(content);

        List<String> metaAuthors = doc.$("meta[name=author]", "content").all();
        if (!metaAuthors.isEmpty()) {
            parser.setAuthor(metaAuthors.get(0));
        } else {
            List<Selectable> authorElements = doc.$(".author, .byline, [rel=author]").nodes();
            parser.setAuthor(authorElements.isEmpty() ? "" : textOf(authorElements.get(0)));
        }

        return parser;
    }

    /** 同一 path 下最多保留的 query 变体数，防止分页/排序链接导致 URL 爆炸 */
    private static final int MAX_QUERY_VARIANTS_PER_PATH = 10;

    public static List<String> extractNextUrls(Html doc, String baseUrl, int remainingDepth) {
        List<String> urls = new ArrayList<>();
        if (remainingDepth <= 0) {
            return urls;
        }
        Set<String> seen = new HashSet<>();
        // path -> 已入队的 query 变体数，用于限制同一 path 下的 query 变体数量
        Map<String, Integer> pathQueryCount = new java.util.HashMap<>();
        for (String href : doc.$("a[href]", "href").all()) {
            String absoluteUrl = resolveUrl(baseUrl, href);
            if (absoluteUrl == null || com.collect.worker.redis.UrlQueueService.isImageUrl(absoluteUrl)) {
                continue;
            }
            String normalizedHref = com.collect.worker.redis.UrlQueueService.normalizeUrl(absoluteUrl);
            if (normalizedHref == null || normalizedHref.isBlank() || !seen.add(normalizedHref)) {
                continue;
            }
            // 按 path 分组限制 query 变体数，防止 ?page=2&sort=asc 等组合导致 URL 爆炸
            String path = pathKey(normalizedHref);
            int count = pathQueryCount.getOrDefault(path, 0);
            if (count >= MAX_QUERY_VARIANTS_PER_PATH) {
                continue;
            }
            pathQueryCount.put(path, count + 1);
            urls.add(normalizedHref);
        }
        return urls;
    }

    /**
     * 提取 URL 的 path 部分（host + path），忽略 query 参数。
     * 用于按 path 分组限制 query 变体数量。
     */
    private static String pathKey(String url) {
        int qIdx = url.indexOf('?');
        return qIdx > 0 ? url.substring(0, qIdx) : url;
    }

    public static boolean isVipPage(Html doc, String selector, String vipContent) {
        if (doc == null || selector == null || selector.isBlank()) {
            return false;
        }
        List<Selectable> matchedElements = doc.$(selector).nodes();
        if (matchedElements.isEmpty()) {
            return false;
        }
        if (vipContent == null || vipContent.isBlank()) {
            return true;
        }
        String content = vipContent.trim();
        return matchedElements.stream().anyMatch(element -> textOf(element).contains(content));
    }

    static String textOf(Selectable selectable) {
        if (selectable instanceof HtmlNode) {
            String text = selectable.xpath("allText()").get();
            return text == null ? "" : text.replaceAll("\\s+", " ").trim();
        }
        return selectable.get() == null ? "" : selectable.get().trim();
    }

    private static String resolveUrl(String baseUrl, String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        try {
            URI resolved = URI.create(baseUrl).resolve(source.trim());
            String scheme = resolved.getScheme();
            if (resolved.getHost() == null || scheme == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                return null;
            }
            return resolved.toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
