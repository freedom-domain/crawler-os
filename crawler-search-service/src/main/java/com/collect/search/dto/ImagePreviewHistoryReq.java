package com.collect.search.dto;

import lombok.Data;

@Data
public class ImagePreviewHistoryReq {

    private String contentId;

    private String title;

    private String url;

    private String spiderName;

    private Integer imageCount;
}
