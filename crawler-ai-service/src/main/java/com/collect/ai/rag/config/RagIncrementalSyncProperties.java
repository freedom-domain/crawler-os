package com.collect.ai.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 内容索引增量向量同步配置。
 * 内容索引（ES 源索引）新增文档后，定时把新文档向量化写入来源是该索引的知识库向量索引。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ai.rag.incremental-sync")
public class RagIncrementalSyncProperties {

    /** 是否开启定时增量同步 */
    private boolean enabled = true;

    /** 同步间隔（毫秒），默认 5 分钟；固定延迟，上一轮结束后才开始计时 */
    private long intervalMs = 300_000L;
}
