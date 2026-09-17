package com.collect.user.dto;

import lombok.Data;

import java.util.List;

@Data
public class RoleAssignPermReq {

    private Long roleId;

    /** 权限ID列表（包含父节点，前端树已回传勾选的全部节点） */
    private List<Long> permissionIds;
}
