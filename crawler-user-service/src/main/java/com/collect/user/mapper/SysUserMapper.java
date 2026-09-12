package com.collect.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT u.*, r.name AS role_name FROM sys_user u " +
            "LEFT JOIN sys_role r ON u.role_id = r.id " +
            "WHERE u.username = #{username} AND u.deleted = 0")
    SysUser selectByUsername(String username);
}
