package com.collect.spider.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.collect.common.result.R;
import com.collect.spider.dto.SpiderCreateReq;
import com.collect.spider.entity.Spider;
import com.collect.spider.entity.SpiderTask;
import com.collect.spider.service.SpiderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "爬虫管理")
@RestController
@RequestMapping("/api/spider")
@RequiredArgsConstructor
public class SpiderController {

    private final SpiderService spiderService;

    @Operation(summary = "创建爬虫")
    @PostMapping
    public R<Spider> create(@Valid @RequestBody SpiderCreateReq req) {
        return R.ok(spiderService.create(req));
    }

    @Operation(summary = "爬虫分页列表")
    @GetMapping("/page")
    public R<IPage<Spider>> page(@RequestParam(defaultValue = "1") int current,
                                 @RequestParam(defaultValue = "10") int size,
                                 @RequestParam(required = false) String keyword) {
        return R.ok(spiderService.page(current, size, keyword));
    }

    @Operation(summary = "爬虫详情")
    @GetMapping("/{id}")
    public R<Spider> detail(@PathVariable Long id) {
        return R.ok(spiderService.getById(id));
    }

    @Operation(summary = "更新爬虫")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Spider spider) {
        spiderService.update(id, spider);
        return R.ok();
    }

    @Operation(summary = "删除爬虫")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        spiderService.delete(id);
        return R.ok();
    }

    @Operation(summary = "启动爬虫")
    @PutMapping("/{id}/start")
    public R<Void> start(@PathVariable Long id) {
        spiderService.start(id);
        return R.ok();
    }

    @Operation(summary = "停止爬虫")
    @PutMapping("/{id}/stop")
    public R<Void> stop(@PathVariable Long id) {
        spiderService.stop(id);
        return R.ok();
    }

    @Operation(summary = "立即执行爬虫")
    @PostMapping("/{id}/run")
    public R<SpiderTask> run(@PathVariable Long id) {
        return R.ok(spiderService.run(id));
    }

    @Operation(summary = "任务分页列表")
    @GetMapping("/task/page")
    public R<IPage<SpiderTask>> taskPage(@RequestParam(defaultValue = "1") int current,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestParam(required = false) Long spiderId,
                                         @RequestParam(required = false) String status) {
        return R.ok(spiderService.taskPage(current, size, spiderId, status));
    }

    @Operation(summary = "任务详情")
    @GetMapping("/task/{id}")
    public R<SpiderTask> taskDetail(@PathVariable Long id) {
        return R.ok(spiderService.taskDetail(id));
    }

    @Operation(summary = "取消任务")
    @PutMapping("/task/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        spiderService.cancelTask(id);
        return R.ok();
    }
}
