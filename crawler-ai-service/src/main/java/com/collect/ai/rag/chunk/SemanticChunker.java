package com.collect.ai.rag.chunk;

import java.util.ArrayList;
import java.util.List;

/**
 * SEMANTIC 分块策略的纯计算部分（无 I/O、无 Spring 依赖，便于单测）。
 *
 * <p>为控制 embedding 调用量，不逐句计算向量边界，而是：
 * <ol>
 *   <li>把文本切成句子（超长句硬切到 chunkSize 以内）；</li>
 *   <li>贪心把句子打包成约 {@code unitTarget} 字符的"语义单元"（见 {@link #packUnits}）；</li>
 *   <li>按单元粒度批量取 embedding：调用量约为句级的 1/8（单元 ≈ 4~10 句）；</li>
 *   <li>流式遍历单元：超限、或相邻单元相似度低于阈值且当前长度足够时切边界，
 *       重叠按整单元回带（见 {@link #advance} / {@link #finish}）。</li>
 * </ol>
 *
 * <p>与逐句版相比，边界分辨率从"每句"降为"每单元"（约 minChunkSize 字符）：
 * 新主题最多在单元边界处进入下一 chunk，边界处可能带出约 1 个单元的旧主题内容，
 * 由 overlap 与父块上下文（small-to-big）缓解。
 */
public final class SemanticChunker {

    private SemanticChunker() {
    }

    /** 硬切时向前回退的句子边界字符（标点/换行）。 */
    private static final String SENTENCE_BOUNDARY_CHARS = "。！？!?；;，,.:：、\n\t";

    // ------------------------------------------------------------------
    // 硬切：固定窗口 + 句界回退 + overlap，供各策略复用
    // ------------------------------------------------------------------

    /**
     * 固定窗口切分：窗口内尽量从最近的句子边界（后半窗口内）下刀，找不到再硬切。
     * overlap 为字符数，0 表示无重叠。
     */
    public static List<String> hardSplit(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) return List.of();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            if (end < text.length()) {
                int boundary = findSentenceBoundary(text, start, end);
                if (boundary > start) end = boundary;
            }
            chunks.add(text.substring(start, end).trim());
            if (end == text.length()) break;
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }

    /**
     * 从切分位置向前查找最近的句子边界（标点/换行），避免从句子中间硬切。
     * 只在后半窗口内查找；返回切分位置（边界字符之后），找不到返回 -1。
     */
    private static int findSentenceBoundary(String text, int start, int end) {
        int minCut = start + Math.max(1, (end - start) / 2);
        for (int i = end - 1; i >= minCut; i--) {
            if (SENTENCE_BOUNDARY_CHARS.indexOf(text.charAt(i)) >= 0) {
                return i + 1;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // 语义分块：句子 -> 语义单元 -> 基于单元向量的边界检测
    // ------------------------------------------------------------------

    /** 按终止标点（保留标点）或换行切句，返回非空句子列表。 */
    public static List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        for (String sentence : text.split("(?<=[。！？!?])\\s*|\\R+")) {
            String value = sentence.trim();
            if (!value.isEmpty()) sentences.add(value);
        }
        return sentences;
    }

    /**
     * 贪心把句子打包成"语义单元"：单元内句子以单空格连接，
     * 加下一句会超过 {@code unitTarget} 字符时收单元；
     * 单句超过 unitTarget 时独占一个单元（调用方需保证句子长度不超过 chunkSize）。
     */
    public static List<String> packUnits(List<String> sentences, int unitTarget) {
        int target = Math.max(1, unitTarget);
        List<String> units = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentLength = 0;
        for (String sentence : sentences) {
            if (currentLength == 0) {
                current.append(sentence);
                currentLength = sentence.length();
                continue;
            }
            if (currentLength + 1 + sentence.length() > target) {
                units.add(current.toString());
                current.setLength(0);
                current.append(sentence);
                currentLength = sentence.length();
            } else {
                current.append(' ').append(sentence);
                currentLength += 1 + sentence.length();
            }
        }
        if (currentLength > 0) units.add(current.toString());
        return units;
    }

    /** 跨 embedding 批次携带的分块状态（回带单元 + 对应向量 + 已连接后的字符数）。 */
    public record CarryState(List<String> units, List<List<Double>> embeddings, int length) {
        public static CarryState empty() {
            return new CarryState(List.of(), List.of(), 0);
        }
    }

    /** 一批处理结果：新完成的 chunk 列表 + 更新后的携带状态。 */
    public record AdvanceResult(List<String> chunks, CarryState state) {
    }

    /**
     * 流式处理一个单元批次（units 与 embeddings 一一对应），
     * 返回新完成的 chunk 和更新后的状态。切分条件与逐句版一致（粒度从句子变为单元）：
     * <ul>
     *   <li>当前长度 + 1 + 单元长度 &gt; chunkSize（超限必切）；或</li>
     *   <li>相邻单元余弦相似度 &lt; threshold 且当前长度 &gt;= minChunkSize（语义边界切分）。</li>
     * </ul>
     * 切分后从上一块末尾回带完整单元作为重叠，避免从单元中间截断。
     */
    public static AdvanceResult advance(
            CarryState state, List<String> units, List<List<Double>> embeddings,
            int chunkSize, int minChunkSize, int overlap, double threshold) {
        List<String> chunks = new ArrayList<>();
        CarryState current = state;
        for (int i = 0; i < units.size(); i++) {
            String unit = units.get(i);
            List<Double> embedding = embeddings.get(i);
            if (!current.units().isEmpty()) {
                boolean sizeLimitReached = current.length() + 1 + unit.length() > chunkSize;
                boolean semanticBoundary = cosineSimilarity(
                        current.embeddings().get(current.embeddings().size() - 1), embedding)
                        < threshold;
                boolean tooSmall = current.length() < minChunkSize;
                if (sizeLimitReached || (semanticBoundary && !tooSmall)) {
                    chunks.add(String.join(" ", current.units()).trim());
                    current = carryBack(current, unit, chunkSize, overlap);
                }
            }
            current = append(current, unit, embedding);
        }
        return new AdvanceResult(chunks, current);
    }

    /** 收尾：把剩余状态输出为最后一个 chunk（无剩余时返回空列表）。 */
    public static List<String> finish(CarryState state) {
        if (state.units().isEmpty()) return List.of();
        return List.of(String.join(" ", state.units()).trim());
    }

    /** 从上一块末尾回带完整单元作为重叠；maxCarry 不足时可能回带为空。 */
    private static CarryState carryBack(CarryState state, String nextUnit, int chunkSize, int overlap) {
        int maxCarry = Math.min(overlap, Math.max(0, chunkSize - nextUnit.length() - 1));
        List<String> carried = new ArrayList<>();
        List<List<Double>> carriedEmbeddings = new ArrayList<>();
        int carriedLength = 0;
        for (int k = state.units().size() - 1; k >= 0; k--) {
            String unit = state.units().get(k);
            int unitLength = unit.length() + (carried.isEmpty() ? 0 : 1);
            if (carriedLength + unitLength > maxCarry) break;
            carried.add(0, unit);
            carriedEmbeddings.add(0, state.embeddings().get(k));
            carriedLength += unitLength;
        }
        return new CarryState(carried, carriedEmbeddings, carriedLength);
    }

    private static CarryState append(CarryState state, String unit, List<Double> embedding) {
        List<String> units = new ArrayList<>(state.units());
        List<List<Double>> embeddings = new ArrayList<>(state.embeddings());
        int length = state.units().isEmpty() ? unit.length() : state.length() + 1 + unit.length();
        units.add(unit);
        embeddings.add(embedding);
        return new CarryState(units, embeddings, length);
    }

    /** 余弦相似度；任一零向量返回 0。 */
    public static double cosineSimilarity(List<Double> left, List<Double> right) {
        double dotProduct = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        int length = Math.min(left.size(), right.size());
        for (int i = 0; i < length; i++) {
            double leftValue = left.get(i);
            double rightValue = right.get(i);
            dotProduct += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }
        if (leftNorm == 0 || rightNorm == 0) return 0;
        return dotProduct / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
