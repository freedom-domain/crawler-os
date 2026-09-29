package com.collect.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.collect.common.exception.BizException;
import com.collect.user.entity.SysPermission;
import com.collect.user.mapper.SysPermissionMapper;
import com.collect.user.mapper.SysRolePermissionMapper;
import com.collect.user.entity.SysRolePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class PermissionService {

    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;

    /** 返回权限树（按 sort 排序） */
    public List<SysPermission> tree() {
        List<SysPermission> all = permissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>().orderByAsc(SysPermission::getSort));
        Map<Long, List<SysPermission>> grouped = all.stream()
                .collect(Collectors.groupingBy(p -> p.getParentId() == null ? 0L : p.getParentId()));
        return buildChildren(grouped, 0L);
    }

    private List<SysPermission> buildChildren(Map<Long, List<SysPermission>> grouped, Long parentId) {
        List<SysPermission> nodes = grouped.getOrDefault(parentId, new ArrayList<>());
        for (SysPermission node : nodes) {
            node.setChildren(buildChildren(grouped, node.getId()));
        }
        return nodes;
    }

    public void create(SysPermission perm) {
        if (perm.getParentId() == null) {
            perm.setParentId(0L);
        }
        if (perm.getSort() == null) {
            perm.setSort(0);
        }
        permissionMapper.insert(perm);
    }

    public void update(Long id, SysPermission perm) {
        SysPermission exist = permissionMapper.selectById(id);
        if (exist == null) {
            throw new BizException("权限不存在");
        }
        if (perm.getParentId() != null && perm.getParentId().equals(id)) {
            throw new BizException("父节点不能是自身");
        }
        exist.setName(perm.getName());
        exist.setCode(perm.getCode());
        if (perm.getParentId() != null) {
            exist.setParentId(perm.getParentId());
        }
        if (perm.getType() != null) {
            exist.setType(perm.getType());
        }
        exist.setPath(perm.getPath());
        exist.setIcon(perm.getIcon());
        if (perm.getSort() != null) {
            exist.setSort(perm.getSort());
        }
        permissionMapper.updateById(exist);
    }

    @Transactional
    public void delete(Long id) {
        SysPermission exist = permissionMapper.selectById(id);
        if (exist == null) {
            throw new BizException("权限不存在");
        }
        Long childCount = permissionMapper.selectCount(
                new LambdaQueryWrapper<SysPermission>().eq(SysPermission::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BizException("存在子权限，无法删除");
        }
        permissionMapper.deleteById(id);
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getPermissionId, id));
    }
}
