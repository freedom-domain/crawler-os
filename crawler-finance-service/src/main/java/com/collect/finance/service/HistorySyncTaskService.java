package com.collect.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.collect.common.exception.BizException;
import com.collect.finance.dto.HistorySyncTaskItemResponse;
import com.collect.finance.dto.HistorySyncTaskResponse;
import com.collect.finance.entity.HistorySyncTask;
import com.collect.finance.entity.HistorySyncTaskItem;
import com.collect.finance.mapper.HistorySyncTaskItemMapper;
import com.collect.finance.mapper.HistorySyncTaskMapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

@Service
public class HistorySyncTaskService {

    private static final Set<String> INSTRUMENT_TYPES = Set.of("FUND", "STOCK");

    private final HistorySyncTaskMapper taskMapper;
    private final HistorySyncTaskItemMapper itemMapper;
    private final HistorySyncTaskRunner runner;

    public HistorySyncTaskService(HistorySyncTaskMapper taskMapper,
                                  HistorySyncTaskItemMapper itemMapper,
                                  HistorySyncTaskRunner runner) {
        this.taskMapper = taskMapper;
        this.itemMapper = itemMapper;
        this.runner = runner;
    }

    public synchronized HistorySyncTaskResponse start(String rawInstrumentType) {
        String instrumentType = rawInstrumentType == null ? "" : rawInstrumentType.trim().toUpperCase(Locale.ROOT);
        if (!INSTRUMENT_TYPES.contains(instrumentType)) {
            throw new BizException("同步类型仅支持 FUND 或 STOCK");
        }
        long activeCount = taskMapper.selectCount(new LambdaQueryWrapper<HistorySyncTask>()
                .eq(HistorySyncTask::getInstrumentType, instrumentType)
                .in(HistorySyncTask::getStatus, "PENDING", "RUNNING", "CANCELLING"));
        if (activeCount > 0) {
            throw new BizException("该类型已有历史行情同步任务正在执行");
        }

        HistorySyncTask task = new HistorySyncTask();
        task.setInstrumentType(instrumentType);
        task.setStatus("PENDING");
        task.setTotalCount(0);
        task.setFinishedCount(0);
        task.setSuccessCount(0);
        task.setFailedCount(0);
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        int total = "FUND".equals(instrumentType)
                ? itemMapper.insertFundItems(task.getId())
                : itemMapper.insertStockItems(task.getId());
        task.setTotalCount(total);
        if (total == 0) {
            task.setStatus("COMPLETED");
            task.setCompletedAt(LocalDateTime.now());
        }
        taskMapper.updateById(task);

        if (total > 0) {
            try {
                runner.executeAsync(task.getId());
            } catch (RejectedExecutionException exception) {
                failUnsubmittedTask(task);
            }
        }
        return get(task.getId(), 1, 10);
    }

    public HistorySyncTaskResponse get(long id, int rawPage, int rawPageSize) {
        HistorySyncTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException("历史行情同步任务不存在");
        }
        return toResponse(task, rawPage, rawPageSize);
    }

    public HistorySyncTaskResponse cancel(long id, int rawPage, int rawPageSize) {
        HistorySyncTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException("历史行情同步任务不存在");
        }
        if ("PENDING".equals(task.getStatus()) && taskMapper.cancelPending(id) == 1) {
            itemMapper.cancelPendingItems(id);
            return get(id, rawPage, rawPageSize);
        }
        if (taskMapper.requestCancellation(id) == 1) {
            return get(id, rawPage, rawPageSize);
        }
        task = taskMapper.selectById(id);
        if (task != null && ("CANCELLING".equals(task.getStatus()) || "CANCELLED".equals(task.getStatus()))) {
            return toResponse(task, rawPage, rawPageSize);
        }
        throw new BizException("历史行情同步任务已结束，无法取消");
    }

    public HistorySyncTaskResponse latest(String rawInstrumentType, int rawPage, int rawPageSize) {
        String instrumentType = rawInstrumentType == null ? "" : rawInstrumentType.trim().toUpperCase(Locale.ROOT);
        if (!INSTRUMENT_TYPES.contains(instrumentType)) {
            throw new BizException("同步类型仅支持 FUND 或 STOCK");
        }
        HistorySyncTask task = taskMapper.selectOne(new LambdaQueryWrapper<HistorySyncTask>()
                .eq(HistorySyncTask::getInstrumentType, instrumentType)
                .orderByDesc(HistorySyncTask::getId)
                .last("LIMIT 1"));
        return task == null ? null : toResponse(task, rawPage, rawPageSize);
    }

    public void markSubmissionFailure(long taskId, String message) {
        HistorySyncTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        itemMapper.update(null, new LambdaUpdateWrapper<HistorySyncTaskItem>()
                .eq(HistorySyncTaskItem::getTaskId, taskId)
                .set(HistorySyncTaskItem::getStatus, "FAILED")
                .set(HistorySyncTaskItem::getMessage, message)
                .set(HistorySyncTaskItem::getFinishedAt, LocalDateTime.now()));
        task.setStatus("FAILED");
        task.setFinishedCount(task.getTotalCount());
        task.setFailedCount(task.getTotalCount());
        task.setMessage(message);
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private void failUnsubmittedTask(HistorySyncTask task) {
        markSubmissionFailure(task.getId(), "同步任务队列已满，请稍后重试");
        task.setStatus("FAILED");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedTasks() {
        List<HistorySyncTask> activeTasks = taskMapper.selectList(new LambdaQueryWrapper<HistorySyncTask>()
                .in(HistorySyncTask::getStatus, "PENDING", "RUNNING", "CANCELLING"));
        for (HistorySyncTask task : activeTasks) {
            if ("CANCELLING".equals(task.getStatus())) {
                itemMapper.cancelPendingItems(task.getId());
                itemMapper.cancelRunningItems(task.getId());
                taskMapper.finishCancellation(task.getId());
                continue;
            }
            itemMapper.update(null, new LambdaUpdateWrapper<HistorySyncTaskItem>()
                    .eq(HistorySyncTaskItem::getTaskId, task.getId())
                    .in(HistorySyncTaskItem::getStatus, "PENDING", "RUNNING")
                    .set(HistorySyncTaskItem::getStatus, "FAILED")
                    .set(HistorySyncTaskItem::getMessage, "Finance 服务重启时任务中断")
                    .set(HistorySyncTaskItem::getFinishedAt, LocalDateTime.now()));
            task.setStatus("FAILED");
            task.setFinishedCount(task.getTotalCount());
            task.setFailedCount(task.getTotalCount() - task.getSuccessCount());
            task.setMessage("Finance 服务重启时任务中断");
            task.setCompletedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        }
    }

    private HistorySyncTaskResponse toResponse(HistorySyncTask task, int rawPage, int rawPageSize) {
        int page = Math.max(rawPage, 1);
        int pageSize = Math.min(Math.max(rawPageSize, 1), 100);
        long offset = (long) (page - 1) * pageSize;
        HistorySyncTaskResponse response = new HistorySyncTaskResponse();
        response.setId(task.getId());
        response.setInstrumentType(task.getInstrumentType());
        response.setStatus(task.getStatus());
        response.setTotalCount(task.getTotalCount());
        response.setFinishedCount(task.getFinishedCount());
        response.setSuccessCount(task.getSuccessCount());
        response.setFailedCount(task.getFailedCount());
        response.setCancelledCount(itemMapper.countCancelledByTaskId(task.getId()));
        response.setCreatedAt(task.getCreatedAt());
        response.setStartedAt(task.getStartedAt());
        response.setCompletedAt(task.getCompletedAt());
        response.setMessage(task.getMessage());
        response.setItemPage(page);
        response.setItemPageSize(pageSize);
        response.setItemTotal((int) itemMapper.countByTaskId(task.getId()));
        response.setItems(itemMapper.selectByTaskId(task.getId(), pageSize, offset)
                .stream().map(this::toItemResponse).toList());
        return response;
    }

    private HistorySyncTaskItemResponse toItemResponse(HistorySyncTaskItem item) {
        HistorySyncTaskItemResponse response = new HistorySyncTaskItemResponse();
        response.setId(item.getId());
        response.setInstrumentType(item.getInstrumentType());
        response.setFundType(item.getFundType());
        response.setCode(item.getCode());
        response.setName(item.getName());
        response.setStatus(item.getStatus());
        response.setMessage(item.getMessage());
        response.setPointCount(item.getPointCount());
        response.setStartedAt(item.getStartedAt());
        response.setFinishedAt(item.getFinishedAt());
        return response;
    }
}
