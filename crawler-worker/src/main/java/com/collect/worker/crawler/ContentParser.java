package com.collect.worker.crawler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
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
        Document doc = Jsoup.parse(html, baseUrl);
        ContentParser parser = new ContentParser();
        parser.setUrl(baseUrl);

        parser.setTitle(doc.title() != null ? doc.title().trim() : baseUrl);

        Element body = doc.body();
        String text = body != null ? body.text() : "";
        parser.setContent(text.length() > 5000 ? text.substring(0, 5000) : text);

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
        int limit = 50;
        Set<String> seen = new HashSet<>();
        for (Element a : doc.select("a[href]")) {
            if (urls.size() >= limit) break;
            String href = a.absUrl("href");
            if (!href.isEmpty() && !href.startsWith("javascript:") && !href.startsWith("mailto:")
                    && !href.startsWith("tel:") && !href.startsWith("#")
                    && seen.add(href)) {
                urls.add(href);
            }
        }
        return urls;
    }

    public static List<String> parseSelectors(String selectorsJson) {
        if (selectorsJson == null || selectorsJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            JSONArray arr = JSON.parseArray(selectorsJson);
            List<String> result = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String sel = obj.getString("selector");
                if (sel != null) {
                    result.add(sel);
                }
            }
            return result;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
