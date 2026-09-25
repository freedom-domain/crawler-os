package com.collect.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.collect.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;


@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT u.*, r.name AS role_name FROM sys_user u " +
            "LEFT JOIN sys_role r ON u.role_id = r.id " +
            "WHERE u.username = #{username} AND u.deleted = 0")
    SysUser selectByUsername(String username);

    @Select("<script>" +
            "SELECT u.*, r.name AS role_name FROM sys_user u " +
            "LEFT JOIN sys_role r ON u.role_id = r.id " +
            "WHERE u.deleted = 0 " +
            "<if test='keyword != null and keyword != \"\"'> AND (u.username LIKE CONCAT('%', #{keyword}, '%') OR u.nickname LIKE CONCAT('%', #{keyword}, '%')) </if> " +
            "ORDER BY u.create_time DESC, u.id DESC" +
            "</script>")
    IPage<SysUser> selectPageWithRole(Page<SysUser> page, @Param("keyword") String keyword);
}
