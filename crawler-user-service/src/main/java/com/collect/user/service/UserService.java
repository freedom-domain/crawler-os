package com.collect.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.collect.common.exception.BizException;
import com.collect.common.security.LoginUser;
import com.collect.common.security.JwtUtils;
import com.collect.user.dto.LoginReq;
import com.collect.user.dto.LoginResp;
import com.collect.user.dto.UserCreateReq;
import com.collect.user.entity.SysPermission;
import com.collect.user.entity.SysRole;
import com.collect.user.entity.SysUser;
import com.collect.user.mapper.SysPermissionMapper;
import com.collect.user.mapper.SysRoleMapper;
import com.collect.user.mapper.SysUserMapper;
import cn.hutool.crypto.SecureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final JwtUtils jwtUtils;

    public LoginResp login(LoginReq req) {
        SysUser user = userMapper.selectByUsername(req.getUsername());
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (!SecureUtil.md5(req.getPassword()).equals(user.getPassword())) {
            throw new BizException("密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException("账号已被禁用");
        }

        List<SysPermission> perms = permissionMapper.selectByRoleId(user.getRoleId());
        List<String> permCodes = perms.stream().map(p -> p.getCode()).collect(Collectors.toList());

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setNickname(user.getNickname());
        loginUser.setRoleId(user.getRoleId());
        loginUser.setRoleName(user.getRoleName());
        loginUser.setPermissions(new java.util.HashSet<>(permCodes));

        String token = jwtUtils.generateToken(loginUser);

        LoginResp resp = new LoginResp();
        resp.setToken(token);
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setNickname(user.getNickname());
        resp.setRole(user.getRoleName());
        resp.setPermissions(permCodes);
        return resp;
    }

    @SuppressWarnings("null")
    public void register(UserCreateReq req) {
        SysUser exist = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
        if (exist != null) {
            throw new BizException("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPassword(SecureUtil.md5(req.getPassword()));
        user.setNickname(req.getNickname() != null ? req.getNickname() : req.getUsername());
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        user.setStatus(1);
        user.setRoleId(req.getRoleId() != null ? req.getRoleId() : 2L);
        userMapper.insert(user);
    }

    @SuppressWarnings("null")
    public IPage<SysUser> page(int current, int size, String keyword) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getNickname, keyword);
        }
        qw.orderByDesc(SysUser::getCreateTime);
        IPage<SysUser> result = userMapper.selectPage(new Page<>(current, size), qw);
        result.getRecords().forEach(u -> u.setPassword(null));
        return result;
    }

    public SysUser getById(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }

    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (!SecureUtil.md5(oldPassword).equals(user.getPassword())) {
            throw new BizException("原密码错误");
        }
        user.setPassword(SecureUtil.md5(newPassword));
        userMapper.updateById(user);
    }

    public List<SysRole> listRoles() {
        return roleMapper.selectList(null);
    }

    public void assignRole(Long userId, Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException("角色不存在");
        }
        SysUser user = new SysUser();
        user.setId(userId);
        user.setRoleId(roleId);
        userMapper.updateById(user);
    }
}
