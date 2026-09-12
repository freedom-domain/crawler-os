package com.collect.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.collect.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String username;
    private String password;
    private String nickname;
    private String email;
    private String phone;
    private Integer status;
    private Long roleId;

    @TableField(exist = false)
    private String roleName;
}
