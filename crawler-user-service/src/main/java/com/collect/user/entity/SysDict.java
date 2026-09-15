package com.collect.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.collect.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict")
public class SysDict extends BaseEntity {

    private Long parentId;
    private String label;
    private String value;
    private Integer sort;
    private Integer status;

    /** 子项列表（非数据库字段） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private java.util.List<SysDict> children;
}
