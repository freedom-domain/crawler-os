package com.collect.common.mq;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class TaskMessage implements Serializable {

    private Long taskId;
    private Long spiderId;
    private String spiderName;
    private String type;
    private List<String> startUrls;
    private String selectors;
    private String imageSelector;
    private Integer maxDepth;
    private Integer timeout;
    private String headers;
}
