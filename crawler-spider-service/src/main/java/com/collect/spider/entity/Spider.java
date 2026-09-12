package com.collect.spider.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.collect.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("spider")
public class Spider extends BaseEntity {

    private String name;
    private String description;
    private String type;
    private String startUrls;
    private String selectors;
    private String schedule;
    private Integer maxDepth;
    private Integer timeout;
    private String headers;
    private Integer enabled;
    private Long creatorId;
    private Integer status;
}
