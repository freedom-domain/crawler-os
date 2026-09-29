package com.collect.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.user.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    @Select("SELECT p.* FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN sys_user u ON u.role_id = rp.role_id " +
            "WHERE u.id = #{userId} AND u.deleted = 0 " +
            "AND p.deleted = 0 AND p.type IN (0, 1) " +
            "ORDER BY p.sort ASC, p.id ASC")
    List<SysPermission> selectMenusByUserId(@Param("userId") Long userId);

    @Select("SELECT p.* FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id " +
            "WHERE rp.role_id = #{roleId} AND p.deleted = 0")
    List<SysPermission> selectByRoleId(Long roleId);
}
