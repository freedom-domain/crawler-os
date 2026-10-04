package com.collect.spider.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.collect.common.exception.BizException;
import com.collect.common.security.LoginUtils;
import com.collect.common.mq.TaskMessage;
import com.collect.spider.dto.SpiderCreateReq;
import com.collect.spider.dto.SpiderImportResult;
import com.collect.spider.dto.SpiderUpdateReq;
import com.collect.spider.dto.TaskConcurrencyResponse;
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
    private static final int MIN_TASK_CONCURRENCY = 1;
    private static final int MAX_TASK_CONCURRENCY = 20;
    private static final int MIN_URL_CONCURRENCY = 1;
    private static final int MAX_URL_CONCURRENCY = 50;
    /** CANCELING 状态超过该时长视为卡死（worker 消费不到 / 未结束），兜底置为 CANCELED */
    private static final int STUCK_CANCELING_MINUTES = 30;

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
        spider.setImageXpath(req.getImageXpath());
        spider.setVipSelector(req.getVipSelector());
        spider.setVipSelectorContent(req.getVipSelectorContent());
        spider.setOverwriteHtml(req.getOverwriteHtml());
        spider.setOverwriteImage(req.getOverwriteImage());
        spider.setReadCache(req.getReadCache() == null ? 0 : req.getReadCache());
        spider.setReadCacheMissOnline(req.getReadCacheMissOnline() == null ? 0 : req.getReadCacheMissOnline());
        spider.setIsPublic(req.getIsPublic() == null ? 0 : req.getIsPublic());
        spider.setGroup(req.getGroup());
        spider.setSchedule(req.getSchedule());
        spider.setMaxDepth(req.getMaxDepth());
        spider.setTimeout(req.getTimeout());
        spider.setHeaders(req.getHeaders());
        spider.setFollowRobots(req.getFollowRobots());
        spider.setSkipTlsVerify(req.getSkipTlsVerify() == null ? 0 : req.getSkipTlsVerify());
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
    public IPage<Spider> page(int current, int size, String keyword, String startUrl, String group, Integer isPublic) {
        current = Math.max(1, current);
        size = Math.min(Math.max(1, size), 100);
        LambdaQueryWrapper<Spider> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.like(Spider::getName, keyword);
        }
        if (startUrl != null && !startUrl.isBlank()) {
            qw.like(Spider::getStartUrls, startUrl.trim());
        }
        if (group != null && !group.isBlank()) {
            qw.eq(Spider::getGroup, group);
        }
        if (isPublic != null) {
            qw.eq(Spider::getIsPublic, isPublic);
        } else if (!isAuthenticated()) {
            qw.eq(Spider::getIsPublic, 1);
        }
        qw.orderByDesc(Spider::getCreateTime).orderByDesc(Spider::getId);
        return spiderMapper.selectPage(new Page<>(current, size), qw);
    }

    private boolean isAuthenticated() {
        try {
            LoginUtils.getUserId();
            return true;
        } catch (Exception ignored) {
            return false;
        }
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
            config.setImageXpath(spider.getImageXpath());
            config.setVipSelector(spider.getVipSelector());
            config.setVipSelectorContent(spider.getVipSelectorContent());
            config.setOverwriteHtml(spider.getOverwriteHtml());
            config.setOverwriteImage(spider.getOverwriteImage());
            config.setReadCache(spider.getReadCache() == null ? 0 : spider.getReadCache());
            config.setReadCacheMissOnline(spider.getReadCacheMissOnline() == null ? 0 : spider.getReadCacheMissOnline());
            config.setIsPublic(spider.getIsPublic() == null ? 0 : spider.getIsPublic());
            config.setGroup(spider.getGroup());
            config.setSchedule(spider.getSchedule());
            config.setMaxDepth(spider.getMaxDepth());
            config.setTimeout(spider.getTimeout());
            config.setHeaders(spider.getHeaders());
            config.setFollowRobots(spider.getFollowRobots());
            config.setSkipTlsVerify(spider.getSkipTlsVerify() == null ? 0 : spider.getSkipTlsVerify());
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
        exist.setImageXpath(req.getImageXpath());
        exist.setVipSelector(req.getVipSelector());
        exist.setVipSelectorContent(req.getVipSelectorContent());
        exist.setOverwriteHtml(req.getOverwriteHtml());
        exist.setOverwriteImage(req.getOverwriteImage());
        exist.setReadCache(req.getReadCache() == null ? 0 : req.getReadCache());
        exist.setReadCacheMissOnline(req.getReadCacheMissOnline() == null ? 0 : req.getReadCacheMissOnline());
        exist.setIsPublic(req.getIsPublic() == null ? 0 : req.getIsPublic());
        exist.setGroup(req.getGroup());
        exist.setSchedule(req.getSchedule());
        exist.setMaxDepth(req.getMaxDepth());
        exist.setTimeout(req.getTimeout());
        exist.setHeaders(req.getHeaders());
        exist.setFollowRobots(req.getFollowRobots());
        exist.setSkipTlsVerify(req.getSkipTlsVerify() == null ? 0 : req.getSkipTlsVerify());
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
        if (spider.getSchedule() == null || spider.getSchedule().isBlank()) {
            throw new BizException("请先设置调度表达式后再启动定时任务");
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
        if (taskMapper.countActiveTasksBySpiderId(spider.getId()) > 0) {
            throw new BizException("该爬虫已有排队或正在运行的任务");
        }

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
        boolean runImmediately = taskMapper.countActiveTasks() < getMaxConcurrency()
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
    public TaskStatsResponse todayTaskStats() {
        return taskMapper.selectTodayTaskStats();
    }

    public long activeTaskCount() {
        return taskMapper.countActiveTasks();
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
    public void pauseTask(Long id) {
        requireTaskCreationGuard();
        SpiderTask task = taskMapper.selectByIdForUpdate(id);
        if (task == null) {
            throw new BizException("任务不存在");
        }
        if (!"RUNNING".equals(task.getStatus())) {
            throw new BizException("仅运行中的任务可暂停");
        }
        taskMapper.setPaused(id);
        log.info("任务已暂停: id={}, taskId={}", id, task.getTaskId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void resumeTask(Long id) {
        requireTaskCreationGuard();
        SpiderTask task = taskMapper.selectByIdForUpdate(id);
        if (task == null) {
            throw new BizException("任务不存在");
        }
        if (task.getPausedAt() == null) {
            throw new BizException("任务未暂停");
        }
        taskMapper.clearPaused(id);
        // 重新发送Kafka消息，让worker重新拾起任务继续爬取
        if (task.getTaskMessage() != null && !task.getTaskMessage().isBlank()) {
            sendTaskAfterCommit(task.getTaskMessage());
        }
        log.info("任务已恢复并重新派发: id={}, taskId={}", id, task.getTaskId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelTask(Long id) {
        requireTaskCreationGuard();
        SpiderTask task = taskMapper.selectByIdForUpdate(id);
        if (task == null) {
            throw new BizException("任务不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if ("PENDING".equals(task.getStatus())) {
            // 排队中直接取消，无需等待派发
            task.setStatus("CANCELED");
            task.setEndTime(now);
            taskMapper.updateById(task);
            log.info("排队任务已取消: id={}, taskId={}", id, task.getTaskId());
        } else if ("RUNNING".equals(task.getStatus())) {
            // 暂停中的任务直接取消，无需等待worker退出
            if (task.getPausedAt() != null) {
                task.setStatus("CANCELED");
                task.setPausedAt(null);
                task.setEndTime(now);
                taskMapper.updateById(task);
                taskMapper.clearPaused(id);
                log.info("暂停中的任务已直接取消: id={}, taskId={}", id, task.getTaskId());
                return;
            }
            task.setStatus("CANCELING");
            taskMapper.updateById(task);
            log.info("任务已置为 CANCELING，等待 worker 消费: id={}, taskId={}", id, task.getTaskId());
        } else if ("CANCELING".equals(task.getStatus())) {
            // 重复取消请求：若已超过卡死阈值（worker 大概率已消费不到），直接兜底置为 CANCELED；
            // 否则维持 CANCELING，等待 worker 正常完成
            LocalDateTime stuckBefore = now.minusMinutes(STUCK_CANCELING_MINUTES);
            if (task.getStartTime() == null || task.getStartTime().isBefore(stuckBefore)) {
                taskMapper.markCancelingTaskCanceled(id, now);
                log.warn("重复取消的卡死任务已兜底置为 CANCELED: id={}, taskId={}", id, task.getTaskId());
            } else {
                log.info("任务已在取消中，维持 CANCELING 等待 worker 完成: id={}, taskId={}", id, task.getTaskId());
            }
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
        msg.setImageXpath(spider.getImageXpath());
        msg.setVipSelector(spider.getVipSelector());
        msg.setVipSelectorContent(spider.getVipSelectorContent());
        msg.setOverwriteHtml(spider.getOverwriteHtml());
        msg.setOverwriteImage(spider.getOverwriteImage());
        msg.setReadCache(spider.getReadCache() == null ? 0 : spider.getReadCache());
        msg.setReadCacheMissOnline(spider.getReadCacheMissOnline() == null ? 0 : spider.getReadCacheMissOnline());
        msg.setMaxDepth(spider.getMaxDepth());
        msg.setTimeout(spider.getTimeout());
        msg.setHeaders(spider.getHeaders());
        msg.setFollowRobots(spider.getFollowRobots());
        msg.setSkipTlsVerify(spider.getSkipTlsVerify() == null ? 0 : spider.getSkipTlsVerify());
        msg.setConcurrency(getMaxConcurrency());
        msg.setUrlConcurrency(getUrlConcurrency());
        return msg;
    }

    /**
     * 强制取消：立即把 CANCELING 任务置为 CANCELED，不等待 worker 完成。
     * 适用于 worker 已挂、Kafka 消息丢失、或用户想立即释放并发槽位的场景。
     */
    @Transactional(rollbackFor = Exception.class)
    public void forceCancelTask(Long id) {
        SpiderTask task = taskMapper.selectById(id);
        if (task == null || task.getDeleted() == 1) {
            throw new BizException("任务不存在");
        }
        if (!"CANCELING".equals(task.getStatus()) && !"RUNNING".equals(task.getStatus())) {
            throw new BizException("只有运行中或取消中的任务可以强制取消");
        }
        LocalDateTime now = LocalDateTime.now();
        taskMapper.markCancelingTaskCanceled(id, now);
        log.warn("任务已强制取消: id={}, taskId={}, previousStatus={}", id, task.getTaskId(), task.getStatus());
    }

    /**
     * 卡死取消任务兜底：
     * 任务进入 CANCELING 后，由 worker 消费取消（worker 完成爬取时把状态写为 CANCELED）。
     * 若 Kafka 消息丢失 / worker 挂掉 / 任务长时间未结束，任务会永远停在 CANCELING，
     * 并持续占用并发槽位（countActiveTasks 包含 CANCELING），阻塞后续任务派发。
     * 这里周期性扫描：进入 CANCELING 超过阈值仍未结束的任务，直接置为 CANCELED。
     */
    @Scheduled(fixedDelayString = "${app.spider.cancel-stuck-check-delay-ms:60000}")
    @Transactional(rollbackFor = Exception.class)
    public void cancelStuckCancelingTasks() {
        LocalDateTime stuckBefore = LocalDateTime.now().minusMinutes(STUCK_CANCELING_MINUTES);
        int stuck = taskMapper.countStuckCancelingTasks(stuckBefore);
        if (stuck <= 0) {
            return;
        }
        log.warn("检测到卡死的取消任务，开始兜底处理: count={}, stuckBefore={}", stuck, stuckBefore);
        int handled = 0;
        for (int i = 0; i < stuck; i++) {
            SpiderTask task = taskMapper.selectOldestStuckCancelingTask(stuckBefore);
            if (task == null) {
                break;
            }
            int updated = taskMapper.markCancelingTaskCanceled(task.getId(), LocalDateTime.now());
            if (updated > 0) {
                handled++;
                log.warn("卡死取消任务已兜底置为 CANCELED: id={}, taskId={}, startTime={}",
                        task.getId(), task.getTaskId(), task.getStartTime());
            }
        }
        if (handled > 0) {
            log.warn("卡死取消任务兜底处理完成: handled={}", handled);
        }
    }

    @Scheduled(fixedDelayString = "${app.spider.queue-poll-delay-ms:1000}")
    @Transactional(rollbackFor = Exception.class)
    public void dispatchNextQueuedTask() {
        requireTaskCreationGuard();
        long activeTasks = taskMapper.countActiveTasks();
        int maxConcurrency = getMaxConcurrency();
        while (activeTasks < maxConcurrency) {
            SpiderTask task = taskMapper.selectNextPendingTaskForUpdate();
            if (task == null) {
                break;
            }
            if (task.getTaskMessage() == null || task.getTaskMessage().isBlank()) {
                task.setStatus("FAILED");
                task.setErrorMessage("任务消息缺失，无法派发");
                task.setEndTime(LocalDateTime.now());
                taskMapper.updateById(task);
                log.error("排队任务缺少消息，已标记失败: taskId={}", task.getTaskId());
                continue;
            }

            task.setStatus("RUNNING");
            task.setStartTime(LocalDateTime.now());
            taskMapper.updateById(task);
            sendTaskAfterCommit(task.getTaskMessage());
            activeTasks++;
            log.info("已派发排队任务: taskId={}, spider={}, activeTasks={}/{}",
                    task.getTaskId(), task.getSpiderName(), activeTasks, maxConcurrency);
        }
    }

    public TaskConcurrencyResponse getTaskConcurrency() {
        requireTaskCreationGuard();
        TaskConcurrencyResponse resp = new TaskConcurrencyResponse();
        resp.setMaxConcurrency(getMaxConcurrency());
        resp.setUrlConcurrency(getUrlConcurrency());
        return resp;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateTaskConcurrency(int maxConcurrency, int urlConcurrency) {
        if (maxConcurrency < MIN_TASK_CONCURRENCY || maxConcurrency > MAX_TASK_CONCURRENCY) {
            throw new BizException("任务并发数必须在 1 到 20 之间");
        }
        if (urlConcurrency < MIN_URL_CONCURRENCY || urlConcurrency > MAX_URL_CONCURRENCY) {
            throw new BizException("URL 并发数必须在 1 到 50 之间");
        }
        requireTaskCreationGuard();
        if (taskMapper.updateConcurrency(maxConcurrency, urlConcurrency) != 1) {
            throw new BizException("任务并发策略保存失败");
        }
        log.info("任务并发策略已更新: maxConcurrency={}, urlConcurrency={}", maxConcurrency, urlConcurrency);
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

    private int getMaxConcurrency() {
        Integer maxConcurrency = taskMapper.selectMaxConcurrency();
        if (maxConcurrency == null
                || maxConcurrency < MIN_TASK_CONCURRENCY
                || maxConcurrency > MAX_TASK_CONCURRENCY) {
            throw new BizException("任务并发策略无效，请检查数据库迁移或配置");
        }
        return maxConcurrency;
    }

    private int getUrlConcurrency() {
        Integer urlConcurrency = taskMapper.selectUrlConcurrency();
        if (urlConcurrency == null
                || urlConcurrency < MIN_URL_CONCURRENCY
                || urlConcurrency > MAX_URL_CONCURRENCY) {
            return 8;
        }
        return urlConcurrency;
    }
}
