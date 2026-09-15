package com.collect.spider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class SpiderCreateReq {

    @NotBlank(message = "爬虫名称不能为空")
    private String name;

    private String description;

    @NotBlank(message = "爬虫类型不能为空")
    private String type;

    @NotEmpty(message = "起始URL不能为空")
    private List<String> startUrls;

    private String selectors;

    private String imageSelector;

    private Integer overwrite = 0;

    private String schedule;

    private Integer maxDepth = 2;

    private Integer timeout = 15000;

    private String headers;

    private Integer enabled = 1;
}
