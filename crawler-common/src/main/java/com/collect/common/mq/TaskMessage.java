package com.collect.common.mq;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class TaskMessage implements Serializable {

    private Long taskId;
    private Long spiderId;
    private String spiderName;
    private String spiderGroup;
    private String type;
    private List<String> startUrls;
    private String contentSelector;
    private String imageSelector;
    private String vipSelector;
    private String vipSelectorContent;
    private Integer overwriteHtml;
    private Integer overwriteImage;
    private Integer readCache;
    private Integer maxDepth;
    private boolean singleUrl;
    private Integer timeout;
    private String headers;
    private Integer followRobots;
}
