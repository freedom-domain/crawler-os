package com.collect.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.collect.common.exception.BizException;
import com.collect.common.security.LoginUser;
import com.collect.common.security.JwtUtils;
import com.collect.user.dto.LoginReq;
import com.collect.user.dto.LoginResp;
import com.collect.user.dto.MenuNode;
import com.collect.user.dto.UserCreateReq;
import com.collect.user.dto.UserUpdateReq;
import com.collect.user.entity.SysPermission;
import com.collect.user.entity.SysRole;
import com.collect.user.entity.SysUser;
import com.collect.user.mapper.SysPermissionMapper;
import com.collect.user.mapper.SysRoleMapper;
import com.collect.user.mapper.SysUserMapper;
import cn.hutool.crypto.SecureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.expire:7200}")
    private long expireSeconds;

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

        SysRole role = roleMapper.selectById(user.getRoleId());
        String roleCode = role != null ? role.getCode() : null;
        boolean isAdmin = "admin".equals(roleCode);

        // 管理员放行所有权限：返回全部权限码，避免依赖角色-权限绑定是否完整
        List<String> permCodes;
        if (isAdmin) {
            permCodes = permissionMapper.selectList(null).stream()
                    .map(p -> p.getCode())
                    .collect(Collectors.toList());
        } else {
            List<SysPermission> perms = permissionMapper.selectByRoleId(user.getRoleId());
            permCodes = perms.stream().map(p -> p.getCode()).collect(Collectors.toList());
        }

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setNickname(user.getNickname());
        loginUser.setRoleId(user.getRoleId());
        loginUser.setRoleName(user.getRoleName());
        loginUser.setRoleCode(roleCode);
        loginUser.setPermissions(new java.util.HashSet<>(permCodes));

        String token = jwtUtils.generateToken(loginUser);

        // 将 token 存入 Redis，设置过期时间
        String jti = jwtUtils.getTokenId(token);
        if (jti != null) {
            redisTemplate.opsForValue().set("token:" + jti, "1", expireSeconds, TimeUnit.SECONDS);
        }

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

    @SuppressWarnings("null")
    public void update(Long id, UserUpdateReq req) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        // 用户名唯一性校验
        if (req.getUsername() != null && !req.getUsername().isBlank()
                && !req.getUsername().equals(user.getUsername())) {
            SysUser exist = userMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
            if (exist != null && !exist.getId().equals(id)) {
                throw new BizException("用户名已存在");
            }
            user.setUsername(req.getUsername());
        }
        if (req.getNickname() != null) {
            user.setNickname(req.getNickname());
        }
        if (req.getEmail() != null) {
            user.setEmail(req.getEmail());
        }
        if (req.getPhone() != null) {
            user.setPhone(req.getPhone());
        }
        if (req.getStatus() != null) {
            user.setStatus(req.getStatus());
        }
        if (req.getRoleId() != null) {
            user.setRoleId(req.getRoleId());
        }
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            user.setPassword(SecureUtil.md5(req.getPassword()));
        }
        userMapper.updateById(user);
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

    public void delete(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if ("admin".equals(user.getUsername())) {
            throw new BizException("不能删除超级管理员");
        }
        userMapper.deleteById(id);
    }

    /**
     * 获取当前用户的菜单（type=0 目录、type=1 菜单）
     */
    public List<MenuNode> getMenu() {
        LoginUser loginUser = com.collect.common.security.LoginUtils.getLoginUser();
        SysUser user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            return List.of();
        }
        List<SysPermission> perms = permissionMapper.selectByRoleId(user.getRoleId());
        // 取 type=0（目录）和 type=1（菜单），按钮 type=2 不展示
        List<SysPermission> menus = perms.stream()
                .filter(p -> p.getType() != null && (p.getType() == 0 || p.getType() == 1))
                .sorted((a, b) -> (a.getSort() != null ? a.getSort() : 0) - (b.getSort() != null ? b.getSort() : 0))
                .collect(Collectors.toList());

        // 构建树形结构
        Map<Long, MenuNode> nodeMap = new java.util.LinkedHashMap<>();
        for (SysPermission p : menus) {
            MenuNode node = new MenuNode();
            node.setId(p.getId());
            node.setName(p.getName());
            node.setCode(p.getCode());
            node.setPath(p.getPath());
            node.setType(p.getType());
            node.setSort(p.getSort());
            nodeMap.put(p.getId(), node);
        }

        List<MenuNode> roots = new java.util.ArrayList<>();
        for (MenuNode node : nodeMap.values()) {
            SysPermission perm = menus.stream().filter(p -> p.getId().equals(node.getId())).findFirst().orElse(null);
            if (perm == null) continue;
            Long parentId = perm.getParentId();
            if (parentId == null || parentId == 0 || !nodeMap.containsKey(parentId)) {
                roots.add(node);
            } else {
                nodeMap.get(parentId).getChildren().add(node);
            }
        }
        return roots;
    }
}
