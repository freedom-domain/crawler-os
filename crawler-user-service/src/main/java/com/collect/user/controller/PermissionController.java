package com.collect.user.controller;

import com.collect.common.result.R;
import com.collect.user.entity.SysPermission;
import com.collect.user.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "权限管理")
@RestController
@RequestMapping("/api/permission")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @Operation(summary = "权限树")
    @GetMapping
    public R<List<SysPermission>> tree() {
        return R.ok(permissionService.tree());
    }

    @Operation(summary = "新增权限")
    @PostMapping
    public R<Void> create(@RequestBody SysPermission perm) {
        permissionService.create(perm);
        return R.ok();
    }

    @Operation(summary = "修改权限")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @RequestBody SysPermission perm) {
        permissionService.update(id, perm);
        return R.ok();
    }

    @Operation(summary = "删除权限")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        permissionService.delete(id);
        return R.ok();
    }
}
