package com.collect.ai.rag.service;

import com.collect.ai.rag.dto.RagDtos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多路召回 RRF 融合（RagService.fuseRrfCandidates）单元测试：
 * 文档分数聚合、代表 chunk 选择（按 RRF 贡献最高而非最先出现）、每文档 chunk 上限。
 */
class RagRecallFusionTest {

    private static RagDtos.SourceReference ref(String docId, String chunkId) {
        RagDtos.SourceReference r = new RagDtos.SourceReference();
        r.setId(docId);
        r.setChunkId(chunkId);
        r.setSnippet(docId + "-" + chunkId);
        return r;
    }

    @Test
    void sameDocMultipleChunksKeepsOnlyHighestContribution() {
        // 单路：d1 的两个 chunk 分别排第 1、4 名 → 上限 1 时只保留贡献最高的 c1
        List<RagDtos.SourceReference> fused = RagService.fuseRrfCandidates(
                List.of(List.of(ref("d1", "c1"), ref("x", "x1"), ref("y", "y1"), ref("d1", "c2"))), 1);

        assertEquals(3, fused.size());
        assertEquals("d1", fused.get(0).getId());
        assertEquals("c1", fused.get(0).getChunkId(), "代表 chunk 应是贡献最高的 c1 而非 c2");
        // d1 文档分 = 1/61 + 1/64 > x(1/62) > y(1/63)
        assertEquals("x", fused.get(1).getId());
        assertEquals("y", fused.get(2).getId());
    }

    @Test
    void representativeIsHighestContributionChunkAcrossPaths() {
        // 路径1: a1(第1名) b1(第2名) c1(第3名)；路径2: a2(第1名) a1(第2名)
        List<List<RagDtos.SourceReference>> paths = List.of(
                List.of(ref("a", "a1"), ref("b", "b1"), ref("c", "c1")),
                List.of(ref("a", "a2"), ref("a", "a1")));
        List<RagDtos.SourceReference> fused = RagService.fuseRrfCandidates(paths, 1);

        assertEquals("a", fused.get(0).getId(), "a 的多路多 chunk 命中应排名最高");
        // a1 贡献 = 1/61(路径1第1名) + 1/62(路径2第2名) > a2 贡献 = 1/61(路径2第1名)
        assertEquals("a1", fused.get(0).getChunkId(), "代表 chunk 应取跨路总贡献最高者");
        assertEquals("b", fused.get(1).getId());
        assertEquals("c", fused.get(2).getId());
    }

    @Test
    void maxChunksPerDocumentKeepsTopNByContribution() {
        // 上限 2：a 的两个 chunk 都保留，且按贡献排序（a2 第1名在前）
        List<List<RagDtos.SourceReference>> paths = List.of(
                List.of(ref("a", "a2"), ref("b", "b1"), ref("a", "a1")));
        List<RagDtos.SourceReference> fused = RagService.fuseRrfCandidates(paths, 2);

        assertEquals(3, fused.size());
        assertEquals("a", fused.get(0).getId());
        assertEquals("a2", fused.get(0).getChunkId());
        assertEquals("a", fused.get(1).getId());
        assertEquals("a1", fused.get(1).getChunkId());
        assertEquals("b", fused.get(2).getId());
        // a 的两个 chunk 共享同一文档 RRF 分数
        assertEquals(fused.get(0).getScore(), fused.get(1).getScore());
    }

    @Test
    void sameChunkAcrossPathsCountsOnceButAccumulatesScore() {
        // 同一 chunk 出现在两路：分数累加，但文档内只保留一个引用
        List<List<RagDtos.SourceReference>> paths = List.of(
                List.of(ref("a", "a1"), ref("b", "b1")),
                List.of(ref("a", "a1")));
        List<RagDtos.SourceReference> fused = RagService.fuseRrfCandidates(paths, 5);

        assertEquals(2, fused.size(), "同一文档同一 chunk 不应重复出现");
        assertEquals("a", fused.get(0).getId());
        assertEquals(2.0 / 61, fused.get(0).getScore(), 1e-12, "分数应按出现次数累加");
    }

    @Test
    void emptyRecallListsReturnEmpty() {
        assertTrue(RagService.fuseRrfCandidates(List.of(), 1).isEmpty());
        assertTrue(RagService.fuseRrfCandidates(List.of(List.of()), 1).isEmpty());
    }
}
