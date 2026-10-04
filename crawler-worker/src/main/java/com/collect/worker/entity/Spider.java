package com.collect.worker.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.collect.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 爬虫配置（worker 侧只读投影，用于读取缓存模式回写起始URL）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("spider")
public class Spider extends BaseEntity {

    private String name;
    private String description;
    private String type;
    private String startUrls;
    private String contentSelector;
    private String imageSelector;
    private String imageXpath;
    private String vipSelector;
    private String vipSelectorContent;
    private Integer overwriteHtml;
    private Integer overwriteImage;
    private Integer readCache;
    private Integer isPublic;
    @TableField("`group`")
    private String group;
    private String schedule;
    private Integer maxDepth;
    private Integer timeout;
    private String headers;
    private Integer followRobots;
    private Integer skipTlsVerify;
    private Integer enabled;
    private Long creatorId;
    private Integer status;
}
