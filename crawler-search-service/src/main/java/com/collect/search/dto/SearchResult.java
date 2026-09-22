package com.collect.search.dto;

import lombok.Data;

import java.util.List;

@Data
public class SearchResult {

    private String id;
    private String title;
    private String content;
    private String url;
    private String author;
    private Long spiderId;
    private String spiderName;
    private String spiderGroup;
    private String sourceType;
    private String crawlTime;
    private String updateTime;
    private List<String> images;
    private List<String> tags;
    private boolean favorited;

    private String titleHl;
    private String contentHl;

    /**
     * search_after 游标值，保留 ES 返回的原始类型，前端下一页时原样回传最后一条的该值。
     */
    private List<Object> sortValues;
}
