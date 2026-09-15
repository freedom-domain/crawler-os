package com.collect.user.controller;

import com.collect.common.result.R;
import com.collect.user.entity.SysDict;
import com.collect.user.service.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "字典管理")
@RestController
@RequestMapping("/api/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    @Operation(summary = "字典树形列表")
    @GetMapping
    public R<List<SysDict>> tree() {
        return R.ok(dictService.tree());
    }

    @Operation(summary = "新增字典项")
    @PostMapping
    public R<Void> create(@RequestBody SysDict dict) {
        dictService.create(dict);
        return R.ok();
    }

    @Operation(summary = "修改字典项")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @RequestBody SysDict dict) {
        dictService.update(id, dict);
        return R.ok();
    }

    @Operation(summary = "删除字典项")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        dictService.delete(id);
        return R.ok();
    }
}
