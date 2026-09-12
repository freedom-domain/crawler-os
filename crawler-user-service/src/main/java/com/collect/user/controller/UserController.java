package com.collect.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.collect.common.result.R;
import com.collect.user.dto.LoginReq;
import com.collect.user.dto.LoginResp;
import com.collect.user.dto.UserCreateReq;
import com.collect.user.entity.SysRole;
import com.collect.user.entity.SysUser;
import com.collect.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "登录")
    @PostMapping("/login")
    public R<LoginResp> login(@Valid @RequestBody LoginReq req) {
        return R.ok(userService.login(req));
    }

    @Operation(summary = "注册")
    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody UserCreateReq req) {
        userService.register(req);
        return R.ok();
    }

    @Operation(summary = "用户分页列表")
    @GetMapping("/page")
    public R<IPage<SysUser>> page(@RequestParam(value = "current", defaultValue = "1") int current,
                                  @RequestParam(value = "size", defaultValue = "10") int size,
                                  @RequestParam(value = "keyword", required = false) String keyword) {
        return R.ok(userService.page(current, size, keyword));
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{id}")
    public R<SysUser> detail(@PathVariable("id") Long id) {
        return R.ok(userService.getById(id));
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public R<Void> updatePassword(@RequestBody Map<String, String> body) {
        userService.updatePassword(Long.valueOf(body.get("userId")),
                body.get("oldPassword"), body.get("newPassword"));
        return R.ok();
    }

    @Operation(summary = "角色列表")
    @GetMapping("/roles")
    public R<List<SysRole>> roles() {
        return R.ok(userService.listRoles());
    }

    @Operation(summary = "分配角色")
    @PutMapping("/assign-role")
    public R<Void> assignRole(@RequestBody Map<String, Long> body) {
        userService.assignRole(body.get("userId"), body.get("roleId"));
        return R.ok();
    }
}
