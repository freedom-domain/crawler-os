package com.collect.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.collect.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

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
}
