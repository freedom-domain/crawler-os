package com.collect.ai.rag.chunk;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticChunkerTest {

    private static List<String> sentences(int count, int length, char ch) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(String.valueOf(ch).repeat(length));
        }
        return list;
    }

    // ------------------------------------------------------------------
    // hardSplit（固定窗口 + 句界回退 + overlap，各策略共用）
    // ------------------------------------------------------------------

    @Test
    void hardSplit_shouldHardCutWhenNoBoundaryFound() {
        assertEquals(List.of("abcd", "efg"), SemanticChunker.hardSplit("abcdefg", 4, 0));
    }

    @Test
    void hardSplit_shouldPreferSentenceBoundaryInBackHalf() {
        // 窗口 7：后半窗口内找到 "。"（下标 3），从其后下刀
        assertEquals(List.of("abc。", "defx"), SemanticChunker.hardSplit("abc。defx", 7, 0));
    }

    @Test
    void hardSplit_shouldCarryOverlapCharsBack() {
        // overlap=1：第二块起点比第一块结尾早 1 个字符（"。"）
        assertEquals(List.of("abc。", "。def", "fgh"), SemanticChunker.hardSplit("abc。defgh", 4, 1));
    }

    @Test
    void hardSplit_shouldReturnEmptyForBlankInput() {
        assertTrue(SemanticChunker.hardSplit("", 10, 0).isEmpty());
        assertTrue(SemanticChunker.hardSplit("   ", 10, 0).isEmpty());
        assertTrue(SemanticChunker.hardSplit(null, 10, 0).isEmpty());
    }

    // ------------------------------------------------------------------
    // splitSentences / packUnits
    // ------------------------------------------------------------------

    @Test
    void splitSentences_shouldSplitOnTerminalPunctuationAndNewlines() {
        assertEquals(List.of("第一句。", "第二句！", "第三句", "第四句"),
                SemanticChunker.splitSentences("第一句。第二句！第三句\n第四句"));
        assertTrue(SemanticChunker.splitSentences("").isEmpty());
        assertTrue(SemanticChunker.splitSentences("  ").isEmpty());
    }

    @Test
    void packUnits_shouldGreedilyFillUpToTarget() {
        List<String> units = SemanticChunker.packUnits(sentences(5, 10, 'a'), 25);
        // 21 + 1 + 10 = 32 > 25，两个句子成单元；最后一个句子单独成单元
        assertEquals(3, units.size());
        assertEquals(21, units.get(0).length());
        assertEquals(21, units.get(1).length());
        assertEquals(10, units.get(2).length());
        assertEquals("aaaaaaaaaa aaaaaaaaaa", units.get(0));
    }

    @Test
    void packUnits_shouldKeepOversizedSentenceAlone() {
        List<String> units = SemanticChunker.packUnits(List.of("x".repeat(30)), 25);
        assertEquals(1, units.size());
        assertEquals(30, units.get(0).length());
    }

    @Test
    void packUnits_shouldReduceEmbeddingCallsVersusSentences() {
        // 40 句 x 30 字符，单元目标 300：embedding 次数从 40 降到约 4~5
        List<String> units = SemanticChunker.packUnits(sentences(40, 30, 's'), 300);
        assertTrue(units.size() < 40 / 4, "单元数应远少于句子数: " + units.size());
        units.forEach(unit -> assertTrue(unit.length() <= 300, "单元不应超过目标长度: " + unit.length()));
    }

    // ------------------------------------------------------------------
    // advance / finish（单元级边界检测，embedding 用二维向量模拟）
    // ------------------------------------------------------------------

    private static final List<Double> A = List.of(1.0, 0.0);
    private static final List<Double> B = List.of(0.0, 1.0);
    private static final List<Double> C = List.of(1.0, 1.0);

    @Test
    void advance_shouldSplitAtSemanticBoundaryBelowThreshold() {
        // chunkSize 20，minChunkSize 5，overlap 0，threshold 0.5
        // a,b 同向（余弦 1）不切；c 与 b 正交（余弦 0 < 0.5）切
        SemanticChunker.AdvanceResult first = SemanticChunker.advance(
                SemanticChunker.CarryState.empty(), List.of("aaaaa", "bbbbb"), List.of(A, A),
                20, 5, 0, 0.5);
        assertTrue(first.chunks().isEmpty());

        SemanticChunker.AdvanceResult second = SemanticChunker.advance(
                first.state(), List.of("ccccc"), List.of(B), 20, 5, 0, 0.5);
        assertEquals(List.of("aaaaa bbbbb"), second.chunks());

        assertEquals(List.of("ccccc"), SemanticChunker.finish(second.state()));
    }

    @Test
    void advance_shouldSplitWhenSizeLimitReached() {
        // chunkSize 11：a+b 恰好 11 不切，再加 c 超限必切（阈值 0.99 排除语义切分）
        SemanticChunker.AdvanceResult first = SemanticChunker.advance(
                SemanticChunker.CarryState.empty(), List.of("aaaaa", "bbbbb"), List.of(A, A),
                11, 5, 0, 0.99);
        assertTrue(first.chunks().isEmpty());
        assertEquals(11, first.state().length());

        SemanticChunker.AdvanceResult second = SemanticChunker.advance(
                first.state(), List.of("ccccc"), List.of(C), 11, 5, 0, 0.99);
        assertEquals(List.of("aaaaa bbbbb"), second.chunks());
        assertEquals(List.of("ccccc"), SemanticChunker.finish(second.state()));
    }

    @Test
    void advance_shouldSkipSemanticSplitWhenTooSmall() {
        // minChunkSize 10：a(5) 后语义边界（余弦 0）出现，但长度不足只允许 size 切分
        SemanticChunker.AdvanceResult first = SemanticChunker.advance(
                SemanticChunker.CarryState.empty(), List.of("aaaaa", "bbbbb"), List.of(A, B),
                20, 10, 0, 0.5);
        assertTrue(first.chunks().isEmpty(), "长度不足 minChunkSize 时语义边界不应切分");

        // 加 c 后长度 11 >= 10，c 与 b 正交（A 与 B 余弦 0）→ 切
        SemanticChunker.AdvanceResult second = SemanticChunker.advance(
                first.state(), List.of("ccccc"), List.of(A), 20, 10, 0, 0.5);
        assertEquals(List.of("aaaaa bbbbb"), second.chunks());
        assertEquals(List.of("ccccc"), SemanticChunker.finish(second.state()));
    }

    @Test
    void advance_shouldCarryWholeUnitsBackAsOverlap() {
        // overlap 10：切分后从上一块末尾回带完整单元 b，b 同时出现在两块中
        SemanticChunker.AdvanceResult first = SemanticChunker.advance(
                SemanticChunker.CarryState.empty(), List.of("aaaaa", "bbbbb"), List.of(A, A),
                20, 5, 10, 0.5);
        SemanticChunker.AdvanceResult second = SemanticChunker.advance(
                first.state(), List.of("ccccc"), List.of(B), 20, 5, 10, 0.5);
        assertEquals(List.of("aaaaa bbbbb"), second.chunks());
        assertEquals(List.of("bbbbb ccccc"), SemanticChunker.finish(second.state()));
    }

    @Test
    void advance_shouldKeepStateAcrossBatches() {
        // 逐单元喂入（模拟 EMBEDDING_BATCH_SIZE=1 的极端批次），结果应与整批一致
        List<String> allUnits = new ArrayList<>(List.of("aaaaa", "bbbbb", "ccccc"));
        List<List<Double>> allEmbeddings = new ArrayList<>(List.of(A, A, B));
        SemanticChunker.CarryState state = SemanticChunker.CarryState.empty();
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < allUnits.size(); i++) {
            SemanticChunker.AdvanceResult result = SemanticChunker.advance(
                    state, List.of(allUnits.get(i)), List.of(allEmbeddings.get(i)),
                    20, 5, 10, 0.5);
            chunks.addAll(result.chunks());
            state = result.state();
        }
        chunks.addAll(SemanticChunker.finish(state));
        assertEquals(List.of("aaaaa bbbbb", "bbbbb ccccc"), chunks);
    }

    @Test
    void cosineSimilarity_shouldHandleOrthogonalAndZeroVectors() {
        assertEquals(1.0, SemanticChunker.cosineSimilarity(A, A), 1e-9);
        assertEquals(0.0, SemanticChunker.cosineSimilarity(A, B), 1e-9);
        assertEquals(0.0, SemanticChunker.cosineSimilarity(List.of(0.0, 0.0), A), 1e-9);
    }
}
