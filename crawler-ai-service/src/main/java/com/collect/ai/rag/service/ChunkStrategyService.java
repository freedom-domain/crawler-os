package com.collect.ai.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.collect.ai.entity.AiRagChunkStrategy;
import com.collect.ai.mapper.AiRagChunkStrategyMapper;
import com.collect.ai.rag.dto.ChunkStrategyRequest;
import com.collect.ai.rag.dto.ChunkStrategyResponse;
import com.collect.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChunkStrategyService {

    public static final String FIXED_CHARACTER = "FIXED_CHARACTER";
    public static final String PARAGRAPH = "PARAGRAPH";
    public static final String RECURSIVE = "RECURSIVE";
    public static final String SEMANTIC = "SEMANTIC";

    private static final Set<String> STRATEGY_TYPES =
            Set.of(FIXED_CHARACTER, PARAGRAPH, RECURSIVE, SEMANTIC);

    private final AiRagChunkStrategyMapper strategyMapper;

    public List<ChunkStrategyResponse> list() {
        return all().stream()
                .map(ChunkStrategyService::toResponse).toList();
    }

    public List<AiRagChunkStrategy> all() {
        return strategyMapper.selectList(new LambdaQueryWrapper<AiRagChunkStrategy>()
                .orderByDesc(AiRagChunkStrategy::getDefaultStrategy)
                .orderByDesc(AiRagChunkStrategy::getPriority)
                .orderByAsc(AiRagChunkStrategy::getId));
    }

    public AiRagChunkStrategy selectForDocument(
            int documentLength, Collection<String> documentTypes,
            List<AiRagChunkStrategy> strategies) {
        AiRagChunkStrategy fallback = strategies.stream()
                .filter(strategy -> Boolean.TRUE.equals(strategy.getDefaultStrategy()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "缺少默认固定字符分块策略，请执行数据库迁移"));
        return strategies.stream()
                .filter(strategy -> !Boolean.TRUE.equals(strategy.getDefaultStrategy()))
                .filter(strategy -> matches(strategy, documentLength, documentTypes))
                .max(java.util.Comparator
                        .comparing(AiRagChunkStrategy::getPriority,
                                java.util.Comparator.nullsFirst(Integer::compareTo))
                        .thenComparing(AiRagChunkStrategy::getId,
                                java.util.Comparator.reverseOrder()))
                .orElse(fallback);
    }

    public AiRagChunkStrategy require(Long id) {
        AiRagChunkStrategy strategy = id == null ? defaultStrategy() : strategyMapper.selectById(id);
        if (strategy == null) throw new BizException("分块策略不存在");
        return strategy;
    }

    public AiRagChunkStrategy defaultStrategy() {
        AiRagChunkStrategy strategy = strategyMapper.selectOne(
                new LambdaQueryWrapper<AiRagChunkStrategy>()
                        .eq(AiRagChunkStrategy::getDefaultStrategy, true)
                        .last("LIMIT 1"));
        if (strategy == null) {
            throw new IllegalStateException("缺少默认固定字符分块策略，请执行数据库迁移");
        }
        return strategy;
    }

    @Transactional
    public ChunkStrategyResponse create(ChunkStrategyRequest request) {
        validate(request);
        ensureUniqueName(request.getName(), null);
        AiRagChunkStrategy strategy = new AiRagChunkStrategy();
        apply(request, strategy);
        strategy.setDefaultStrategy(false);
        strategy.setCreateTime(LocalDateTime.now());
        strategy.setUpdateTime(strategy.getCreateTime());
        if (strategyMapper.insert(strategy) != 1) {
            throw new IllegalStateException("创建分块策略失败");
        }
        return toResponse(strategy);
    }

    @Transactional
    public ChunkStrategyResponse update(Long id, ChunkStrategyRequest request) {
        validate(request);
        AiRagChunkStrategy strategy = require(id);
        if (Boolean.TRUE.equals(strategy.getDefaultStrategy())
                && !FIXED_CHARACTER.equalsIgnoreCase(request.getStrategyType().trim())) {
            throw new BizException("默认分块策略必须保持为固定字符");
        }
        ensureUniqueName(request.getName(), id);
        apply(request, strategy);
        strategy.setUpdateTime(LocalDateTime.now());
        if (strategyMapper.updateById(strategy) != 1) {
            throw new IllegalStateException("保存分块策略失败");
        }
        return toResponse(strategy);
    }

    @Transactional
    public void delete(Long id) {
        AiRagChunkStrategy strategy = require(id);
        if (Boolean.TRUE.equals(strategy.getDefaultStrategy())) {
            throw new BizException("默认固定字符策略不能删除");
        }
        if (strategyMapper.deleteById(id) != 1) {
            throw new IllegalStateException("删除分块策略失败");
        }
    }

    private void ensureUniqueName(String name, Long excludedId) {
        LambdaQueryWrapper<AiRagChunkStrategy> query = new LambdaQueryWrapper<AiRagChunkStrategy>()
                .eq(AiRagChunkStrategy::getName, name.trim());
        if (excludedId != null) query.ne(AiRagChunkStrategy::getId, excludedId);
        if (strategyMapper.selectCount(query) > 0) {
            throw new BizException("分块策略名称已存在");
        }
    }

    private static void apply(ChunkStrategyRequest request, AiRagChunkStrategy strategy) {
        String type = request.getStrategyType().trim().toUpperCase(Locale.ROOT);
        strategy.setName(request.getName().trim());
        strategy.setStrategyType(type);
        strategy.setChunkSize(request.getChunkSize());
        strategy.setChunkOverlap(SEMANTIC.equals(type)
                ? 0 : request.getChunkOverlap());
        strategy.setSimilarityThreshold(request.getSimilarityThreshold() == null
                ? new BigDecimal("0.70") : request.getSimilarityThreshold());
        strategy.setPriority(request.getPriority() == null ? 0 : request.getPriority());
        strategy.setMinDocumentLength(request.getMinDocumentLength() == null
                ? 0 : request.getMinDocumentLength());
        strategy.setMaxDocumentLength(request.getMaxDocumentLength());
        strategy.setDocumentTypes(normalizeDocumentTypes(request.getDocumentTypes()));
    }

    private static void validate(ChunkStrategyRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())
                || !StringUtils.hasText(request.getStrategyType())) {
            throw new BizException("分块策略名称和类型不能为空");
        }
        String type = request.getStrategyType().trim().toUpperCase(Locale.ROOT);
        if (!STRATEGY_TYPES.contains(type)) {
            throw new BizException("不支持的分块策略类型");
        }
        if (request.getName().trim().length() > 128) {
            throw new BizException("分块策略名称不能超过 128 个字符");
        }
        if (request.getPriority() != null
                && (request.getPriority() < 0 || request.getPriority() > 10000)) {
            throw new BizException("策略优先级范围为 0–10000");
        }
        if ((request.getMinDocumentLength() != null && request.getMinDocumentLength() < 0)
                || (request.getMaxDocumentLength() != null
                && request.getMaxDocumentLength() < 1)) {
            throw new BizException("文档字符数范围不能为负数");
        }
        if (request.getMaxDocumentLength() != null
                && request.getMinDocumentLength() != null
                && request.getMaxDocumentLength() < request.getMinDocumentLength()) {
            throw new BizException("文档最大字符数不能小于最小字符数");
        }
        if (request.getDocumentTypes() != null && request.getDocumentTypes().length() > 512) {
            throw new BizException("文档类型配置不能超过 512 个字符");
        }
        if (request.getChunkSize() == null
                || request.getChunkSize() < 200 || request.getChunkSize() > 20000) {
            throw new BizException("最大分块长度范围为 200–20000");
        }
        if (!SEMANTIC.equals(type) && (request.getChunkOverlap() == null
                || request.getChunkOverlap() < 0
                || request.getChunkOverlap() >= request.getChunkSize())) {
            throw new BizException("分块重叠必须大于等于 0 且小于最大分块长度");
        }
        BigDecimal threshold = request.getSimilarityThreshold();
        if (threshold != null && (threshold.compareTo(BigDecimal.ZERO) < 0
                || threshold.compareTo(BigDecimal.ONE) > 0)) {
            throw new BizException("语义相似度阈值必须在 0 到 1 之间");
        }
    }

    private static ChunkStrategyResponse toResponse(AiRagChunkStrategy strategy) {
        ChunkStrategyResponse response = new ChunkStrategyResponse();
        response.setId(strategy.getId());
        response.setName(strategy.getName());
        response.setStrategyType(strategy.getStrategyType());
        response.setChunkSize(strategy.getChunkSize());
        response.setChunkOverlap(strategy.getChunkOverlap());
        response.setSimilarityThreshold(strategy.getSimilarityThreshold());
        response.setPriority(strategy.getPriority());
        response.setMinDocumentLength(strategy.getMinDocumentLength());
        response.setMaxDocumentLength(strategy.getMaxDocumentLength());
        response.setDocumentTypes(strategy.getDocumentTypes());
        response.setDefaultStrategy(Boolean.TRUE.equals(strategy.getDefaultStrategy()));
        return response;
    }

    private static boolean matches(
            AiRagChunkStrategy strategy, int documentLength, Collection<String> documentTypes) {
        int minLength = strategy.getMinDocumentLength() == null
                ? 0 : strategy.getMinDocumentLength();
        Integer maxLength = strategy.getMaxDocumentLength();
        if (documentLength < minLength || maxLength != null && documentLength > maxLength) {
            return false;
        }
        if (!StringUtils.hasText(strategy.getDocumentTypes())) return true;
        if (documentTypes == null || documentTypes.isEmpty()) return false;
        Set<String> configuredTypes = Set.of(strategy.getDocumentTypes().split(","));
        return documentTypes.stream().map(ChunkStrategyService::normalizeDocumentType)
                .anyMatch(configuredTypes::contains);
    }

    private static String normalizeDocumentTypes(String types) {
        if (!StringUtils.hasText(types)) return "";
        return java.util.Arrays.stream(types.split("[,;\\s]+"))
                .map(ChunkStrategyService::normalizeDocumentType)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static String normalizeDocumentType(String type) {
        if (type == null) return "";
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(".")) normalized = normalized.substring(1);
        return normalized;
    }
}
