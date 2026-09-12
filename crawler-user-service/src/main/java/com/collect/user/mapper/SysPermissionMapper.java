package com.collect.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.user.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    @Select("SELECT p.* FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id " +
            "WHERE rp.role_id = #{roleId} AND p.deleted = 0")
    List<SysPermission> selectByRoleId(Long roleId);
}
