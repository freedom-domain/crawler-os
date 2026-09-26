package com.collect.spider.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.collect.common.exception.BizException;
import com.collect.common.result.R;
import com.collect.common.security.LoginUtils;
import com.collect.spider.dto.SpiderCreateReq;
import com.collect.spider.dto.SpiderImportResult;
import com.collect.spider.dto.SpiderRerunReq;
import com.collect.spider.dto.SpiderUpdateReq;
import com.collect.spider.dto.TaskStatsResponse;
import com.collect.spider.entity.Spider;
import com.collect.spider.entity.SpiderTask;
import com.collect.spider.entity.SpiderTaskLog;
import com.collect.spider.service.SpiderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.alibaba.fastjson2.JSON;

import java.util.List;

@Tag(name = "爬虫管理")
@RestController
@RequestMapping("/api/spider")
@RequiredArgsConstructor
public class SpiderController {

    private final SpiderService spiderService;

    @Operation(summary = "创建爬虫")
    @PostMapping
    public R<Spider> create(@Valid @RequestBody SpiderCreateReq req) {
        requirePermission("spider:create");
        return R.ok(spiderService.create(req));
    }

    @Operation(summary = "导出爬虫配置")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        requirePermission("spider:create");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=spiders.json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(spiderService.exportAll());
    }

    @Operation(summary = "导入爬虫配置")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<SpiderImportResult> importConfigs(@RequestParam("file") MultipartFile file) {
        requirePermission("spider:create");
        if (file == null || file.isEmpty()) {
            throw new BizException("导入文件不能为空");
        }
        try {
            List<SpiderCreateReq> configs = JSON.parseArray(
                    new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8),
                    SpiderCreateReq.class);
            return R.ok(spiderService.importConfigs(configs));
        } catch (Exception e) {
            throw new BizException("导入文件格式错误");
        }
    }

    @Operation(summary = "爬虫分页列表")
    @GetMapping("/page")
    public R<IPage<Spider>> page(@RequestParam(value = "current", defaultValue = "1") int current,
                                  @RequestParam(value = "size", defaultValue = "10") int size,
                                  @RequestParam(value = "keyword", required = false) String keyword,
                                  @RequestParam(value = "startUrl", required = false) String startUrl,
                                  @RequestParam(value = "group", required = false) String group) {
        return R.ok(spiderService.page(current, size, keyword, startUrl, group));
    }

    @Operation(summary = "爬虫详情")
    @GetMapping("/{id}")
    public R<Spider> detail(@PathVariable("id") Long id) {
        return R.ok(spiderService.getById(id));
    }

    @Operation(summary = "更新爬虫")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @Valid @RequestBody SpiderUpdateReq req) {
        requirePermission("spider:create");
        spiderService.update(id, req);
        return R.ok();
    }

    @Operation(summary = "删除爬虫")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        requirePermission("spider:create");
        spiderService.delete(id);
        return R.ok();
    }

    @Operation(summary = "启动爬虫")
    @PutMapping("/{id}/start")
    public R<Void> start(@PathVariable("id") Long id) {
        requirePermission("spider:run");
        spiderService.start(id);
        return R.ok();
    }

    @Operation(summary = "停止爬虫")
    @PutMapping("/{id}/stop")
    public R<Void> stop(@PathVariable("id") Long id) {
        requirePermission("spider:run");
        spiderService.stop(id);
        return R.ok();
    }

    @Operation(summary = "立即执行爬虫")
    @PostMapping("/{id}/run")
    public R<SpiderTask> run(@PathVariable("id") Long id) {
        requirePermission("spider:run");
        return R.ok(spiderService.run(id));
    }

    @Operation(summary = "重新爬取单条记录")
    @PostMapping("/{id}/rerun")
    public R<SpiderTask> rerun(@PathVariable("id") Long id, @Valid @RequestBody SpiderRerunReq req) {
        requirePermission("spider:run");
        return R.ok(spiderService.rerun(id, req.getUrl()));
    }

    private void requirePermission(String code) {
        if (!LoginUtils.hasPermission(code)) {
            throw new BizException("无权限执行该操作");
        }
    }

    /**
     * 仅当存在登录用户时校验权限；匿名访问（无登录用户）直接放行。
     * 用于允许公开页面匿名读取，同时保证登录用户仍需具备相应权限。
     */
    private void requirePermissionIfLoggedIn(String code) {
        try {
            LoginUtils.getLoginUser();
        } catch (Exception e) {
            // 未登录（匿名访问），放行
            return;
        }
        if (!LoginUtils.hasPermission(code)) {
            throw new BizException("无权限执行该操作");
        }
    }

    @Operation(summary = "任务分页列表")
    @GetMapping("/task/page")
    public R<IPage<SpiderTask>> taskPage(@RequestParam(value = "current", defaultValue = "1") int current,
                                          @RequestParam(value = "size", defaultValue = "10") int size,
                                          @RequestParam(value = "spiderId", required = false) Long spiderId,
                                          @RequestParam(value = "status", required = false) String status) {
        return R.ok(spiderService.taskPage(current, size, spiderId, status));
    }

    @Operation(summary = "最近任务列表")
    @GetMapping("/task/recent")
    public R<List<SpiderTask>> recentTasks(@RequestParam(value = "size", defaultValue = "5") int size) {
        return R.ok(spiderService.recentTasks(size));
    }

    @Operation(summary = "今日任务统计")
    @GetMapping("/task/stats")
    public R<TaskStatsResponse> todayTaskStats() {
        return R.ok(spiderService.todayTaskStats());
    }

    @Operation(summary = "获取任务最大并发数")
    @GetMapping("/task/concurrency")
    public R<Integer> taskConcurrency() {
        requirePermission("spider:run");
        return R.ok(spiderService.getTaskConcurrency());
    }

    @Operation(summary = "更新任务最大并发数")
    @PutMapping("/task/concurrency")
    public R<Void> updateTaskConcurrency(@RequestParam("maxConcurrency") int maxConcurrency) {
        requirePermission("spider:run");
        spiderService.updateTaskConcurrency(maxConcurrency);
        return R.ok();
    }

    @Operation(summary = "任务详情")
    @GetMapping("/task/{id}")
    public R<SpiderTask> taskDetail(@PathVariable("id") Long id) {
        return R.ok(spiderService.taskDetail(id));
    }

    @Operation(summary = "任务日志")
    @GetMapping("/task/{id}/logs")
    public R<IPage<SpiderTaskLog>> taskLogs(@PathVariable("id") Long id,
                                              @RequestParam(value = "current", defaultValue = "1") int current,
                                              @RequestParam(value = "size", defaultValue = "50") int size,
                                              @RequestParam(value = "status", required = false) Integer status,
                                              @RequestParam(value = "level", required = false) String level,
                                              @RequestParam(value = "type", required = false) String type,
                                              @RequestParam(value = "keyword", required = false) String keyword) {
        SpiderTask task = spiderService.taskDetail(id);
        if (task == null) {
            return R.ok();
        }
        return R.ok(spiderService.taskLogPage(task.getId(), current, size, status, level, type, keyword));
    }

    @Operation(summary = "取消任务")
    @PutMapping("/task/{id}/cancel")
    public R<Void> cancel(@PathVariable("id") Long id) {
        spiderService.cancelTask(id);
        return R.ok();
    }

    @Operation(summary = "删除任务")
    @DeleteMapping("/task/{id}")
    public R<Void> deleteTask(@PathVariable("id") Long id) {
        spiderService.deleteTask(id);
        return R.ok();
    }
}
