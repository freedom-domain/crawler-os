package com.collect.ai.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.collect.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("spider")
public class Spider extends BaseEntity {
    private String name;
    private Integer isPublic;

    @TableField("`group`")
    private String group;
}
