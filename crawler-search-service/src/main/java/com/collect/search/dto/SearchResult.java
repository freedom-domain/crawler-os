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
}
