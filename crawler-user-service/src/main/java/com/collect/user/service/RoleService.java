package com.collect.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.collect.common.exception.BizException;
import com.collect.user.dto.RoleAssignPermReq;
import com.collect.user.dto.RoleSaveReq;
import com.collect.user.entity.SysRole;
import com.collect.user.entity.SysRolePermission;
import com.collect.user.mapper.SysRoleMapper;
import com.collect.user.mapper.SysRolePermissionMapper;
import com.collect.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class RoleService {

    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserMapper userMapper;

    public List<SysRole> list() {
        return roleMapper.selectList(
                new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId));
    }

    public SysRole getById(Long id) {
        return roleMapper.selectById(id);
    }

    public void create(RoleSaveReq req) {
        SysRole exist = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, req.getCode()));
        if (exist != null) {
            throw new BizException("角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setName(req.getName());
        role.setCode(req.getCode());
        role.setDescription(req.getDescription());
        role.setStatus(req.getStatus() != null ? req.getStatus() : 1);
        roleMapper.insert(role);
    }

    public void update(Long id, RoleSaveReq req) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException("角色不存在");
        }
        if (!role.getCode().equals(req.getCode())) {
            SysRole exist = roleMapper.selectOne(
                    new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, req.getCode()));
            if (exist != null && !exist.getId().equals(id)) {
                throw new BizException("角色编码已存在");
            }
        }
        role.setName(req.getName());
        role.setCode(req.getCode());
        role.setDescription(req.getDescription());
        if (req.getStatus() != null) {
            role.setStatus(req.getStatus());
        }
        roleMapper.updateById(role);
    }

    public void delete(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException("角色不存在");
        }
        if ("admin".equals(role.getCode())) {
            throw new BizException("不能删除管理员角色");
        }
        Long userCount = userMapper.selectCount(
                new LambdaQueryWrapper<com.collect.user.entity.SysUser>().eq(com.collect.user.entity.SysUser::getRoleId, id));
        if (userCount != null && userCount > 0) {
            throw new BizException("该角色下存在用户，无法删除");
        }
        roleMapper.deleteById(id);
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, id));
    }

    /** 查询角色已拥有的权限ID列表 */
    public List<Long> listPermissionIds(Long roleId) {
        List<SysRolePermission> list = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, roleId));
        return list.stream().map(SysRolePermission::getPermissionId).toList();
    }

    /** 全量覆盖角色权限 */
    @Transactional
    public void assignPermissions(RoleAssignPermReq req) {
        if (req.getRoleId() == null) {
            throw new BizException("角色ID不能为空");
        }
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, req.getRoleId()));
        if (req.getPermissionIds() != null && !req.getPermissionIds().isEmpty()) {
            for (Long permId : req.getPermissionIds()) {
                SysRolePermission rp = new SysRolePermission();
                rp.setRoleId(req.getRoleId());
                rp.setPermissionId(permId);
                rolePermissionMapper.insert(rp);
            }
        }
    }
}
