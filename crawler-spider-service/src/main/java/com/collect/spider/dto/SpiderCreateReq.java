package com.collect.spider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    private String contentSelector;

    private String imageSelector;

    private String vipSelector;

    private String vipSelectorContent;

    private Integer overwriteHtml = 0;
    private Integer overwriteImage = 0;

    @Min(value = 0, message = "是否公开只能设置为0或1")
    @Max(value = 1, message = "是否公开只能设置为0或1")
    private Integer isPublic = 0;

    private String group;

    private String schedule;

    private Integer maxDepth = 2;

    private Integer timeout = 15000;

    private String headers;

    private Integer followRobots = 0;

    private Integer enabled = 1;
}
