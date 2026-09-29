package com.collect.user.controller;

import com.collect.common.result.R;
import com.collect.user.dto.RoleAssignPermReq;
import com.collect.user.dto.RoleSaveReq;
import com.collect.user.entity.SysRole;
import com.collect.user.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "角色管理")
@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "角色列表")
    @GetMapping
    public R<List<SysRole>> list() {
        return R.ok(roleService.list());
    }

    @Operation(summary = "角色详情")
    @GetMapping("/{id}")
    public R<SysRole> detail(@PathVariable("id") Long id) {
        return R.ok(roleService.getById(id));
    }

    @Operation(summary = "新增角色")
    @PostMapping
    public R<Void> create(@Valid @RequestBody RoleSaveReq req) {
        roleService.create(req);
        return R.ok();
    }

    @Operation(summary = "修改角色")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @Valid @RequestBody RoleSaveReq req) {
        roleService.update(id, req);
        return R.ok();
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        roleService.delete(id);
        return R.ok();
    }

    @Operation(summary = "查询角色已分配权限ID")
    @GetMapping("/{id}/permissions")
    public R<List<Long>> listPermissions(@PathVariable("id") Long id) {
        return R.ok(roleService.listPermissionIds(id));
    }

    @Operation(summary = "分配角色权限")
    @PutMapping("/permissions")
    public R<Void> assignPermissions(@RequestBody RoleAssignPermReq req) {
        roleService.assignPermissions(req);
        return R.ok();
    }
}
