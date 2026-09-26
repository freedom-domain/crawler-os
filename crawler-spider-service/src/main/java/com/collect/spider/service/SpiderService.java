package com.collect.spider.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.collect.common.exception.BizException;
import com.collect.common.mq.TaskMessage;
import com.collect.spider.dto.SpiderCreateReq;
import com.collect.spider.dto.SpiderImportResult;
import com.collect.spider.dto.SpiderUpdateReq;
import com.collect.spider.dto.TaskStatsResponse;
import com.collect.spider.entity.Spider;
import com.collect.spider.entity.SpiderTask;
import com.collect.spider.entity.SpiderTaskLog;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpiderService {

    @Value("${app.kafka.spider-task-topic}")
    private String spiderTaskTopic;

    private final com.collect.spider.mapper.SpiderMapper spiderMapper;
    private final com.collect.spider.mapper.SpiderTaskMapper taskMapper;
    private final com.collect.spider.mapper.SpiderTaskLogMapper logMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @SuppressWarnings("null")
    public Spider create(SpiderCreateReq req) {
        String name = req.getName().trim();
        Spider exist = spiderMapper.selectOne(
                new LambdaQueryWrapper<Spider>().eq(Spider::getName, name));
        if (exist != null) {
            throw new BizException("爬虫名称已存在");
        }
        Spider spider = new Spider();
        spider.setName(name);
        spider.setDescription(req.getDescription());
        spider.setType(req.getType());
        spider.setStartUrls(JSON.toJSONString(req.getStartUrls()));
        spider.setContentSelector(req.getContentSelector());
        spider.setImageSelector(req.getImageSelector());
        spider.setVipSelector(req.getVipSelector());
        spider.setVipSelectorContent(req.getVipSelectorContent());
        spider.setOverwriteHtml(req.getOverwriteHtml());
        spider.setOverwriteImage(req.getOverwriteImage());
        spider.setGroup(req.getGroup());
        spider.setSchedule(req.getSchedule());
        spider.setMaxDepth(req.getMaxDepth());
        spider.setTimeout(req.getTimeout());
        spider.setHeaders(req.getHeaders());
        spider.setFollowRobots(req.getFollowRobots());
        spider.setEnabled(req.getEnabled());
        spider.setCreatorId(1L);
        spider.setStatus(0);
        try {
            spiderMapper.insert(spider);
        } catch (DuplicateKeyException e) {
            throw new BizException("爬虫名称已存在");
        }
        return spider;
    }

    @SuppressWarnings("null")
    public IPage<Spider> page(int current, int size, String keyword, String group) {
        current = Math.max(1, current);
        size = Math.min(Math.max(1, size), 100);
        LambdaQueryWrapper<Spider> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.like(Spider::getName, keyword);
        }
        if (group != null && !group.isBlank()) {
            qw.eq(Spider::getGroup, group);
        }
        qw.orderByDesc(Spider::getCreateTime).orderByDesc(Spider::getId);
        return spiderMapper.selectPage(new Page<>(current, size), qw);
    }

    public Spider getById(Long id) {
        return spiderMapper.selectById(id);
    }

    public byte[] exportAll() {
        List<SpiderCreateReq> configs = new ArrayList<>();
        for (Spider spider : spiderMapper.selectList(null)) {
            SpiderCreateReq config = new SpiderCreateReq();
            config.setName(spider.getName());
            config.setDescription(spider.getDescription());
            config.setType(spider.getType());
            config.setStartUrls(JSON.parseArray(spider.getStartUrls(), String.class));
            config.setContentSelector(spider.getContentSelector());
            config.setImageSelector(spider.getImageSelector());
            config.setVipSelector(spider.getVipSelector());
            config.setVipSelectorContent(spider.getVipSelectorContent());
            config.setOverwriteHtml(spider.getOverwriteHtml());
            config.setOverwriteImage(spider.getOverwriteImage());
            config.setGroup(spider.getGroup());
            config.setSchedule(spider.getSchedule());
            config.setMaxDepth(spider.getMaxDepth());
            config.setTimeout(spider.getTimeout());
            config.setHeaders(spider.getHeaders());
            config.setFollowRobots(spider.getFollowRobots());
            config.setEnabled(spider.getEnabled());
            configs.add(config);
        }
        return JSON.toJSONString(configs).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Transactional
    public SpiderImportResult importConfigs(List<SpiderCreateReq> configs) {
        int imported = 0;
        int skipped = 0;
        int failed = 0;
        if (configs == null) {
            return new SpiderImportResult(0, 0, 0);
        }
        for (SpiderCreateReq config : configs) {
            if (config == null || config.getName() == null || config.getName().isBlank()
                    || config.getType() == null || config.getType().isBlank()
                    || config.getStartUrls() == null || config.getStartUrls().isEmpty()) {
                failed++;
                continue;
            }
            config.setName(config.getName().trim());
            Spider existing = spiderMapper.selectOne(
                    new LambdaQueryWrapper<Spider>().eq(Spider::getName, config.getName()));
            if (existing != null) {
                skipped++;
                continue;
            }
            try {
                create(config);
                imported++;
            } catch (RuntimeException e) {
                log.warn("导入爬虫失败: name={}", config.getName(), e);
                failed++;
            }
        }
        return new SpiderImportResult(imported, skipped, failed);
    }

    public void update(Long id, SpiderUpdateReq req) {
        Spider exist = spiderMapper.selectById(id);
        if (exist == null) {
            throw new BizException("爬虫不存在");
        }
        String name = req.getName().trim();
        @SuppressWarnings("null")
        Spider other = spiderMapper.selectOne(
                new LambdaQueryWrapper<Spider>().eq(Spider::getName, name).ne(Spider::getId, id));
        if (other != null) {
            throw new BizException("爬虫名称已存在");
        }
        exist.setName(name);
        exist.setDescription(req.getDescription());
        exist.setType(req.getType());
        exist.setStartUrls(JSON.toJSONString(req.getStartUrls()));
        exist.setContentSelector(req.getContentSelector());
        exist.setImageSelector(req.getImageSelector());
        exist.setVipSelector(req.getVipSelector());
        exist.setVipSelectorContent(req.getVipSelectorContent());
        exist.setOverwriteHtml(req.getOverwriteHtml());
        exist.setOverwriteImage(req.getOverwriteImage());
        exist.setGroup(req.getGroup());
        exist.setSchedule(req.getSchedule());
        exist.setMaxDepth(req.getMaxDepth());
        exist.setTimeout(req.getTimeout());
        exist.setHeaders(req.getHeaders());
        exist.setFollowRobots(req.getFollowRobots());
        try {
            spiderMapper.updateById(exist);
        } catch (DuplicateKeyException e) {
            throw new BizException("爬虫名称已存在");
        }
    }

    public void delete(Long id) {
        Spider spider = spiderMapper.selectById(id);
        if (spider == null) {
            throw new BizException("爬虫不存在");
        }
        spiderMapper.deleteById(id);
    }

    public void start(Long id) {
        Spider spider = spiderMapper.selectById(id);
        if (spider == null) {
            throw new BizException("爬虫不存在");
        }
        spider.setStatus(1);
        spider.setEnabled(1);
        spiderMapper.updateById(spider);
    }

    public void stop(Long id) {
        Spider spider = spiderMapper.selectById(id);
        if (spider == null) {
            throw new BizException("爬虫不存在");
        }
        spider.setStatus(0);
        spiderMapper.updateById(spider);
    }

    @Transactional(rollbackFor = Exception.class)
    public SpiderTask run(Long id) {
        Spider spider = spiderMapper.selectById(id);
        if (spider == null) {
            throw new BizException("爬虫不存在");
        }
        return createTask(spider, JSON.parseArray(spider.getStartUrls(), String.class), null, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public SpiderTask rerun(Long id, String url) {
        Spider spider = spiderMapper.selectById(id);
        if (spider == null) {
            throw new BizException("爬虫不存在");
        }
        if (url == null || url.isBlank()) {
            throw new BizException("URL不能为空");
        }
        return createTask(spider, List.of(url), 0, true);
    }

    private SpiderTask createTask(Spider spider, List<String> startUrls, Integer maxDepthOverride, boolean forceOverwrite) {
        requireTaskCreationGuard();

        Long taskId = snowflakeId();
        TaskMessage msg = buildMessage(spider, taskId, startUrls);
        if (maxDepthOverride != null) {
            msg.setMaxDepth(maxDepthOverride);
            msg.setSingleUrl(true);
        }
        if (forceOverwrite) {
            msg.setOverwriteHtml(1);
            msg.setOverwriteImage(1);
        }
        String payload = JSON.toJSONString(msg);
        boolean runImmediately = taskMapper.countActiveTasks() == 0
                && taskMapper.selectNextPendingTaskForUpdate() == null;

        SpiderTask task = new SpiderTask();
        task.setSpiderId(spider.getId());
        task.setSpiderName(spider.getName());
        task.setStatus(runImmediately ? "RUNNING" : "PENDING");
        if (runImmediately) {
            task.setStartTime(LocalDateTime.now());
        }
        task.setSuccessCount(0);
        task.setFailCount(0);
        task.setTaskId(taskId);
        task.setTaskMessage(payload);
        taskMapper.insert(task);
        log.info("任务已创建: id={}, taskId={}", task.getId(), taskId);

        if (runImmediately) {
            sendTaskAfterCommit(payload);
            log.info("已派发爬虫任务: taskId={}, spider={}", taskId, spider.getName());
        } else {
            log.info("爬虫任务已加入等待队列: taskId={}, spider={}", taskId, spider.getName());
        }
        return task;
    }

    private Long snowflakeId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 999);
    }

    @SuppressWarnings("null")
    public IPage<SpiderTask> taskPage(int current, int size, Long spiderId, String status) {
        current = Math.max(1, current);
        size = Math.min(Math.max(1, size), 100);
        return taskMapper.selectTaskPage(new Page<>(current, size), spiderId, status);
    }

    @SuppressWarnings("null")
    public List<SpiderTask> recentTasks(int limit) {
        return taskMapper.selectRecentTasks(Math.max(1, Math.min(limit, 20)));
    }

    @SuppressWarnings("null")
    public TaskStatsResponse todayTaskStats() {
        return taskMapper.selectTodayTaskStats();
    }

    @SuppressWarnings("null")
    public SpiderTask taskDetail(Long id) {
        return taskMapper.selectTaskById(id);
    }

    @SuppressWarnings("null")
    public IPage<SpiderTaskLog> taskLogPage(Long taskId, int current, int size,
                                             Integer status, String level, String type, String keyword) {
        current = Math.max(1, current);
        size = Math.min(Math.max(1, size), 100);
        LambdaQueryWrapper<SpiderTaskLog> qw = new LambdaQueryWrapper<>();
        qw.eq(SpiderTaskLog::getTaskId, taskId);
        if (status != null) {
            qw.eq(SpiderTaskLog::getStatus, status);
        }
        if (level != null && !level.isBlank()) {
            qw.eq(SpiderTaskLog::getLevel, level);
        }
        if (type != null && !type.isBlank()) {
            qw.eq(SpiderTaskLog::getType, type);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(SpiderTaskLog::getUrl, keyword)
                         .or().like(SpiderTaskLog::getMessage, keyword));
        }
        qw.orderByDesc(SpiderTaskLog::getCreateTime);
        return logMapper.selectPage(new Page<>(current, size), qw);
    }

    @SuppressWarnings("null")
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long id) {
        requireTaskCreationGuard();
        SpiderTask task = taskMapper.selectByIdForUpdate(id);
        if (task == null) {
            throw new BizException("任务不存在");
        }
        if ("RUNNING".equals(task.getStatus()) || "CANCELING".equals(task.getStatus())) {
            throw new BizException("运行中的任务不可删除，请先取消任务");
        }
        log.info("删除任务: id={}, taskId={}", id, task.getTaskId());
        // 任务日志的 task_id 字段存储的是任务的主键 id
        int deletedLogs = logMapper.physicalDeleteByTaskId(id);
        log.info("删除任务日志: taskId={}, 删除数量={}", id, deletedLogs);
        taskMapper.physicalDeleteById(id);
        log.info("删除任务记录: id={}", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelTask(Long id) {
        requireTaskCreationGuard();
        SpiderTask task = taskMapper.selectByIdForUpdate(id);
        if (task == null) {
            throw new BizException("任务不存在");
        }
        if ("PENDING".equals(task.getStatus())) {
            LocalDateTime endTime = LocalDateTime.now();
            task.setStatus("CANCELED");
            task.setEndTime(endTime);
            taskMapper.updateById(task);
        } else if ("RUNNING".equals(task.getStatus())) {
            task.setStatus("CANCELING");
            taskMapper.updateById(task);
        }
    }

    private TaskMessage buildMessage(Spider spider, Long taskId) {
        return buildMessage(spider, taskId, JSON.parseArray(spider.getStartUrls(), String.class));
    }

    private TaskMessage buildMessage(Spider spider, Long taskId, List<String> startUrls) {
        TaskMessage msg = new TaskMessage();
        msg.setTaskId(taskId);
        msg.setSpiderId(spider.getId());
        msg.setSpiderName(spider.getName());
        msg.setSpiderGroup(spider.getGroup());
        msg.setType(spider.getType());
        msg.setStartUrls(startUrls);
        msg.setContentSelector(spider.getContentSelector());
        msg.setImageSelector(spider.getImageSelector());
        msg.setVipSelector(spider.getVipSelector());
        msg.setVipSelectorContent(spider.getVipSelectorContent());
        msg.setOverwriteHtml(spider.getOverwriteHtml());
        msg.setOverwriteImage(spider.getOverwriteImage());
        msg.setMaxDepth(spider.getMaxDepth());
        msg.setTimeout(spider.getTimeout());
        msg.setHeaders(spider.getHeaders());
        msg.setFollowRobots(spider.getFollowRobots());
        return msg;
    }

    @Scheduled(fixedDelayString = "${app.spider.queue-poll-delay-ms:1000}")
    @Transactional(rollbackFor = Exception.class)
    public void dispatchNextQueuedTask() {
        requireTaskCreationGuard();
        if (taskMapper.countActiveTasks() > 0) {
            return;
        }

        SpiderTask task = taskMapper.selectNextPendingTaskForUpdate();
        if (task == null) {
            return;
        }
        if (task.getTaskMessage() == null || task.getTaskMessage().isBlank()) {
            task.setStatus("FAILED");
            task.setErrorMessage("任务消息缺失，无法派发");
            task.setEndTime(LocalDateTime.now());
            taskMapper.updateById(task);
            log.error("排队任务缺少消息，已标记失败: taskId={}", task.getTaskId());
            return;
        }

        task.setStatus("RUNNING");
        task.setStartTime(LocalDateTime.now());
        taskMapper.updateById(task);
        sendTaskAfterCommit(task.getTaskMessage());
        log.info("已派发排队任务: taskId={}, spider={}", task.getTaskId(), task.getSpiderName());
    }

    private void sendTaskAfterCommit(String payload) {
        String topic = Objects.requireNonNull(spiderTaskTopic, "Kafka task topic must be configured");
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Task dispatch requires an active database transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                kafkaTemplate.send(topic, payload);
            }
        });
    }

    private void requireTaskCreationGuard() {
        if (taskMapper.lockTaskCreation() == null) {
            throw new BizException("任务队列未初始化，请执行数据库迁移");
        }
    }
}
