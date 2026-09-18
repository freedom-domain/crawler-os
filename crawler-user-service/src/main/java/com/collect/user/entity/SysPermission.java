package com.collect.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.collect.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class SysPermission extends BaseEntity {

    private String name;
    private String code;
    private Long parentId;
    private Integer type;
    private String path;
    private Integer sort;

    /** 子权限（仅用于树形展示，非数据库字段） */
    @TableField(exist = false)
    private List<SysPermission> children = new ArrayList<>();
}
