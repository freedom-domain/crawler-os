package com.collect.spider.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SpiderRerunReq {

    @NotBlank(message = "URL不能为空")
    private String url;
}