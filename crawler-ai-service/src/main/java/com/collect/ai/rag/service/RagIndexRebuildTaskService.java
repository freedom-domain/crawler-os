package com.collect.ai.rag.service;

import com.collect.ai.rag.dto.RagDtos;
import com.collect.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class RagIndexRebuildTaskService {

    private static final int MAX_TASKS = 100;

    private final RagService ragService;
    private final ThreadPoolTaskExecutor ragIndexRebuildExecutor;
    private final Map<String, RagDtos.SyncTaskResponse> tasks = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    public RagIndexRebuildTaskService(
            RagService ragService,
            @Qualifier("ragIndexRebuildExecutor")
            ThreadPoolTaskExecutor ragIndexRebuildExecutor) {
        this.ragService = ragService;
        this.ragIndexRebuildExecutor = ragIndexRebuildExecutor;
    }

    public synchronized RagDtos.SyncTaskResponse start(Long knowledgeBaseId) {
        for (RagDtos.SyncTaskResponse task : tasks.values()) {
            if (equalsKnowledgeBase(task.getKnowledgeBaseId(), knowledgeBaseId)
                    && ("PENDING".equals(task.getStatus()) || "RUNNING".equals(task.getStatus()))) {
                throw new BizException("该知识库的向量索引重建任务正在执行");
            }
        }
        if (tasks.size() >= MAX_TASKS) {
            tasks.entrySet().removeIf(entry -> {
                if (isFinished(entry.getValue())) {
                    cancelFlags.remove(entry.getKey());
                    return true;
                }
                return false;
            });
        }
        if (tasks.size() >= MAX_TASKS) {
            throw new BizException("向量索引重建任务过多，请稍后重试");
        }

        long now = System.currentTimeMillis();
        RagDtos.SyncTaskResponse task = new RagDtos.SyncTaskResponse();
        task.setTaskId(UUID.randomUUID().toString());
        task.setKnowledgeBaseId(knowledgeBaseId);
        task.setStatus("PENDING");
        task.setMessage("等待执行");
        task.setCreatedAt(now);
        tasks.put(task.getTaskId(), task);
        cancelFlags.put(task.getTaskId(), new AtomicBoolean(false));

        try {
            ragIndexRebuildExecutor.execute(() -> execute(task.getTaskId()));
        } catch (RejectedExecutionException exception) {
            fail(task.getTaskId(), "向量索引重建任务队列已满，请稍后重试");
        }
        return snapshot(task);
    }

    public RagDtos.SyncTaskResponse get(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException("任务 ID不能为空");
        }
        RagDtos.SyncTaskResponse task = tasks.get(taskId);
        if (task == null) {
            throw new BizException("向量索引重建任务不存在");
        }
        return snapshot(task);
    }

    /** 返回该知识库最新的重建任务；无任务时返回 null */
    public RagDtos.SyncTaskResponse latest(Long knowledgeBaseId) {
        RagDtos.SyncTaskResponse latest = null;
        for (RagDtos.SyncTaskResponse task : tasks.values()) {
            if (!equalsKnowledgeBase(task.getKnowledgeBaseId(), knowledgeBaseId)) {
                continue;
            }
            if (latest == null || task.getCreatedAt() > latest.getCreatedAt()) {
                latest = task;
            }
        }
        return latest == null ? null : snapshot(latest);
    }

    private void execute(String taskId) {
        RagDtos.SyncTaskResponse task = tasks.get(taskId);
        if (task == null) {
            return;
        }
        AtomicBoolean cancelled = cancelFlags.get(taskId);
        if (cancelled != null && cancelled.get()
                && "PENDING".equals(task.getStatus())) {
            task.setStatus("CANCELED");
            task.setMessage("向量索引重建已取消");
            task.setCompletedAt(System.currentTimeMillis());
            return;
        }
        task.setStatus("RUNNING");
        task.setMessage("正在重建向量索引");
        task.setStartedAt(System.currentTimeMillis());
        try {
            RagDtos.SyncResult result = ragService.syncContentIndex(task.getKnowledgeBaseId(),
                    () -> cancelled != null && cancelled.get());
            if (cancelled != null && cancelled.get()) {
                throw new RagService.SyncCancelledException();
            }
            task.setStatus("COMPLETED");
            task.setMessage("向量索引重建完成");
            task.setResult(result);
        } catch (RagService.SyncCancelledException exception) {
            log.info("向量索引重建已取消: taskId={}, knowledgeBaseId={}",
                    taskId, task.getKnowledgeBaseId());
            task.setStatus("CANCELED");
            task.setMessage("向量索引重建已取消");
        } catch (RuntimeException exception) {
            log.error("向量索引重建失败: taskId={}, knowledgeBaseId={}",
                    taskId, task.getKnowledgeBaseId(), exception);
            task.setStatus("FAILED");
            task.setMessage(exception.getMessage() == null ? "向量索引重建失败" : exception.getMessage());
        } finally {
            task.setCompletedAt(System.currentTimeMillis());
        }
    }

    /**
     * 取消任务。PENDING 任务立即标记为 CANCELED；RUNNING 任务在下一个检查点
     * （每个文档或每批 embedding 前）中断，期间状态保持 RUNNING 并提示正在取消。
     */
    public RagDtos.SyncTaskResponse cancel(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BizException("任务 ID不能为空");
        }
        RagDtos.SyncTaskResponse task = tasks.get(taskId);
        if (task == null) {
            throw new BizException("向量索引重建任务不存在");
        }
        if (!("PENDING".equals(task.getStatus()) || "RUNNING".equals(task.getStatus()))) {
            throw new BizException("任务已结束，无法取消");
        }
        cancelFlags.computeIfAbsent(taskId, id -> new AtomicBoolean(false)).set(true);
        if ("PENDING".equals(task.getStatus())) {
            task.setStatus("CANCELED");
            task.setMessage("向量索引重建已取消");
            task.setCompletedAt(System.currentTimeMillis());
        } else {
            task.setMessage("正在取消向量索引重建");
        }
        log.info("向量索引重建取消请求: taskId={}, knowledgeBaseId={}, status={}",
                taskId, task.getKnowledgeBaseId(), task.getStatus());
        return snapshot(task);
    }

    private void fail(String taskId, String message) {
        RagDtos.SyncTaskResponse task = tasks.get(taskId);
        if (task == null) {
            return;
        }
        task.setStatus("FAILED");
        task.setMessage(message);
        task.setStartedAt(System.currentTimeMillis());
        task.setCompletedAt(System.currentTimeMillis());
    }

    private static boolean equalsKnowledgeBase(Long left, Long right) {
        return left == null ? right == null : left.equals(right);
    }

    private static boolean isFinished(RagDtos.SyncTaskResponse task) {
        return "COMPLETED".equals(task.getStatus()) || "FAILED".equals(task.getStatus())
                || "CANCELED".equals(task.getStatus());
    }

    private static RagDtos.SyncTaskResponse snapshot(RagDtos.SyncTaskResponse source) {
        RagDtos.SyncTaskResponse copy = new RagDtos.SyncTaskResponse();
        copy.setTaskId(source.getTaskId());
        copy.setKnowledgeBaseId(source.getKnowledgeBaseId());
        copy.setStatus(source.getStatus());
        copy.setMessage(source.getMessage());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setStartedAt(source.getStartedAt());
        copy.setCompletedAt(source.getCompletedAt());
        RagDtos.SyncResult result = source.getResult();
        if (result != null) {
            RagDtos.SyncResult resultCopy = new RagDtos.SyncResult();
            resultCopy.setSourceDocuments(result.getSourceDocuments());
            resultCopy.setChunks(result.getChunks());
            resultCopy.setVectorDocuments(result.getVectorDocuments());
            copy.setResult(resultCopy);
        }
        return copy;
    }
}
