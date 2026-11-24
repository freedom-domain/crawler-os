package com.collect.ai.rag.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.collect.ai.entity.AiKnowledgeBase;
import com.collect.ai.mapper.AiKnowledgeBaseMapper;
import com.collect.ai.rag.config.RagIncrementalSyncProperties;
import com.collect.ai.rag.dto.RagDtos;
import com.collect.ai.rag.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 内容索引增量向量同步定时任务。
 *
 * <p>内容索引（爬虫写入的 ES 源索引）新增文档后，本任务定时把所有
 * “启用中 + 来源为内容索引（ES_INDEX）”的知识库做增量向量化：
 * 只把尚未写入知识库向量索引的新文档分块、生成 embedding 并写入，
 * 已向量化文档不重复处理。全量重建仍由管理端手动触发。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagIncrementalSyncScheduler {

    private final RagService ragService;
    private final AiKnowledgeBaseMapper knowledgeBaseMapper;
    private final RagIncrementalSyncProperties properties;

    /** 防止上一轮尚未结束时下一轮重复触发 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${app.ai.rag.incremental-sync.interval-ms:300000}")
    public void syncNewContentDocuments() {
        if (!properties.isEnabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.info("内容索引增量同步：上一轮尚未结束，跳过本次触发");
            return;
        }
        try {
            List<AiKnowledgeBase> bases = knowledgeBaseMapper.selectList(
                    new LambdaQueryWrapper<AiKnowledgeBase>()
                            .eq(AiKnowledgeBase::getSourceType, "ES_INDEX")
                            .eq(AiKnowledgeBase::getEnabled, true));
            if (bases.isEmpty()) {
                return;
            }
            for (AiKnowledgeBase base : bases) {
                try {
                    RagDtos.SyncResult result = ragService.syncNewContentDocuments(base, () -> false);
                    if (result.getSourceDocuments() > 0) {
                        log.info("内容索引增量同步：知识库 [{}] 新增向量化 {} 篇文档、{} 个分块",
                                base.getName(), result.getSourceDocuments(), result.getChunks());
                    }
                } catch (RuntimeException e) {
                    // 单个知识库失败不影响其他知识库
                    log.warn("内容索引增量同步失败：知识库 [{}] (id={})：{}",
                            base.getName(), base.getId(), e.getMessage());
                }
            }
        } finally {
            running.set(false);
        }
    }
}
