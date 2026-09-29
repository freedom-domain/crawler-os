package com.collect.search.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.collect.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 爬虫实体（只读），用于按分组查询爬虫 ID。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("spider")
public class Spider extends BaseEntity {

    private String name;

    private Integer isPublic;

    @TableField("`group`")
    private String group;
}
