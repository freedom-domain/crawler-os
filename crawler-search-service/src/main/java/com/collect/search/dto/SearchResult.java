package com.collect.search.dto;

import lombok.Data;

@Data
public class SearchResult {

    private String id;
    private String title;
    private String content;
    private String url;
    private String author;
    private Long spiderId;
    private String spiderName;
    private String sourceType;
    private String crawlTime;

    private String titleHl;
    private String contentHl;
}
