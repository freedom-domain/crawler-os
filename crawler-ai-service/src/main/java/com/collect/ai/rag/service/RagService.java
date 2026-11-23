package com.collect.ai.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.collect.ai.entity.AiRagExcludedDocument;
import com.collect.ai.entity.AiRagChunkStrategy;
import com.collect.ai.entity.AiKnowledgeBase;
import com.collect.ai.entity.AiRagChatModel;
import com.collect.ai.entity.AiRagVectorModel;
import com.collect.ai.entity.AiRagRerankModel;
import com.collect.ai.entity.Spider;
import com.collect.ai.mapper.AiRagExcludedDocumentMapper;
import com.collect.ai.mapper.AiKnowledgeBaseMapper;
import com.collect.ai.mapper.AiRagChatModelMapper;
import com.collect.ai.mapper.AiRagVectorModelMapper;
import com.collect.ai.mapper.AiRagRerankModelMapper;
import com.collect.ai.entity.AiKnowledgeDocument;
import com.collect.ai.mapper.AiKnowledgeDocumentMapper;
import com.collect.ai.mapper.SpiderMapper;
import com.collect.ai.rag.config.RagProperties;
import com.collect.ai.rag.dto.AiAssistantDtos;
import com.collect.ai.rag.dto.KnowledgeBaseDtos;
import com.collect.ai.rag.dto.RagDtos;
import com.collect.ai.rag.dto.AiKnowledgeDocumentDtos;
import com.collect.common.exception.BizException;
import com.collect.common.security.LoginUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.helpers.DefaultHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Comparator;
import java.util.HashSet;
import java.util.function.BooleanSupplier;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private static final int EMBEDDING_BATCH_SIZE = 16;
    private static final HttpClient EMBEDDING_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final HttpClient ELASTICSEARCH_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String NO_RETRIEVED_RECORDS_ANSWER =
            "当前知识库中没有检索到相关记录，请调整问题或先添加相关知识库文档。";

    private record StreamingChatResult(
            String answer, long waitDurationMs, Long thinkingDurationMs, long generationDurationMs) {
    }

    private record KnowledgeVectorChunk(
            String documentId, int chunkIndex, String title, String heading, String content,
            String parentText, Long strategyId, String strategyName) {
    }

    /** 父块（small-to-big）最大长度：检索命中子块后，用父块给 LLM 提供更完整上下文 */
    private static final int PARENT_CHUNK_MAX_LENGTH = 4000;

    private record ChunkingResult(List<String> chunks, AiRagChunkStrategy strategy,
                                  List<String> headings, List<String> parentTexts) {
    }

    /** 按 Markdown 标题层级切出的文本段，heading 为标题路径（如 "一级 / 二级"） */
    private record HeadingSection(String heading, String text) {
    }

    private static final Pattern MARKDOWN_HEADING_PATTERN =
            Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");

    private final RagProperties properties;
    private final ObjectMapper objectMapper;
    private final SpiderMapper spiderMapper;
    private final AiRagExcludedDocumentMapper excludedDocumentMapper;
    private final ChunkStrategyService chunkStrategyService;
    private final AiKnowledgeBaseMapper knowledgeBaseMapper;
    private final AiRagVectorModelMapper vectorModelMapper;
    private final AiRagRerankModelMapper rerankModelMapper;
    private final AiRagChatModelMapper chatModelMapper;
    private final RerankService rerankService;
    private final AiKnowledgeDocumentMapper knowledgeDocumentMapper;
    private final MinioClient minioClient;

    @Value("${spring.elasticsearch.uris:http://127.0.0.1:9200}")
    private String elasticsearchUris;

    @Value("${spring.elasticsearch.username:}")
    private String elasticsearchUsername;

    @Value("${spring.elasticsearch.password:}")
    private String elasticsearchPassword;
    @Value("${minio.bucket:crawler-knowledge}")
    private String uploadBucket;

    /** 向量索引重建被取消时抛出（协作式取消） */
    public static class SyncCancelledException extends RuntimeException {
        public SyncCancelledException() {
            super("向量索引重建已取消");
        }
    }

    public RagDtos.SyncResult syncContentIndex(Long knowledgeBaseId) {
        return syncContentIndex(knowledgeBaseId, () -> false);
    }

    /**
     * 按知识库重建向量索引。
     * cancelChecker 在分块循环的检查点（每个文档、每批 embedding）被调用，
     * 返回 true 时抛出 SyncCancelledException 中断重建。
     */
    public RagDtos.SyncResult syncContentIndex(Long knowledgeBaseId, BooleanSupplier cancelChecker) {
        if (knowledgeBaseId == null) return syncContentIndex();
        AiKnowledgeBase base = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (base == null) throw new BizException("知识库不存在");
        AiRagVectorModel model = vectorModelMapper.selectById(base.getVectorModelId());
        if (model == null) throw new BizException("知识库向量模型不存在");
        List<AiRagChunkStrategy> chunkStrategies = chunkStrategyService.all();
        String vectorIndex = resolveKnowledgeVectorIndex(model);
        Long ownerKnowledgeBaseId = "UPLOAD".equalsIgnoreCase(base.getSourceType()) ? base.getId() : null;
        List<JsonNode> documents = fetchContentDocuments(properties.resolveSourceIndex(), ownerKnowledgeBaseId);
        ensureKnowledgeVectorIndex(base, model);
        deleteKnowledgeVectors(vectorIndex, base.getId());
        List<ObjectNode> operations = new ArrayList<>();
        List<KnowledgeVectorChunk> embeddingBatch = new ArrayList<>(EMBEDDING_BATCH_SIZE);
        long chunks = 0;
        for (JsonNode document : documents) {
            if (cancelChecker.getAsBoolean()) {
                throw new SyncCancelledException();
            }
            JsonNode source = document.path("_source");
            String id = document.path("_id").asText();
            String title = source.path("title").asText("");
            String content = source.path("content").asText("");
            ChunkingResult chunking = splitText(documentText(title, content), documentTypes(source),
                    chunkStrategies, model);
            List<String> parts = chunking.chunks();
            for (int i = 0; i < parts.size(); i++) {
                embeddingBatch.add(new KnowledgeVectorChunk(id, i, title,
                        chunking.headings().get(i), parts.get(i),
                        chunking.parentTexts().get(i),
                        chunking.strategy().getId(), chunking.strategy().getName()));
                if (embeddingBatch.size() == EMBEDDING_BATCH_SIZE) {
                    if (cancelChecker.getAsBoolean()) {
                        throw new SyncCancelledException();
                    }
                    chunks += indexKnowledgeBaseChunkBatch(
                            base, model, embeddingBatch, operations);
                    embeddingBatch.clear();
                }
            }
        }
        if (!embeddingBatch.isEmpty()) {
            chunks += indexKnowledgeBaseChunkBatch(base, model, embeddingBatch, operations);
        }
        if (!operations.isEmpty()) {
            bulkOperations(operations, false);
        }
        refreshIndex(vectorIndex);
        RagDtos.SyncResult result = new RagDtos.SyncResult();
        result.setSourceDocuments(documents.size());
        result.setChunks(chunks);
        result.setVectorDocuments(chunks);
        return result;
    }

    private long indexKnowledgeBaseChunkBatch(
            AiKnowledgeBase base,
            AiRagVectorModel model,
            List<KnowledgeVectorChunk> batch,
            List<ObjectNode> operations) {
        List<String> texts = batch.stream().map(KnowledgeVectorChunk::content).toList();
        List<List<Double>> embeddings = createEmbeddings(texts, model, model.getDimensions());
        for (int i = 0; i < batch.size(); i++) {
            KnowledgeVectorChunk chunk = batch.get(i);
            String chunkId = chunk.documentId() + ":" + chunk.chunkIndex();
            operations.add(objectMapper.createObjectNode()
                    .set("index", objectMapper.createObjectNode()
                            .put("_index", resolveKnowledgeVectorIndex(model)).put("_id", chunkId)));
            ObjectNode row = objectMapper.createObjectNode();
            row.put("doc_id", chunk.documentId()).put("chunk_id", chunkId)
                    .put("title", chunk.title()).put("heading", chunk.heading())
                    .put("content", chunk.content()).put("parent_text", chunk.parentText())
                    .put("url", "").put("spiderName", "上传文档")
                    .put("knowledgeBaseId", base.getId())
                    .put("embedding_model", model.getEmbeddingModel())
                    .put("chunk_strategy_id", chunk.strategyId())
                    .put("chunk_strategy_name", chunk.strategyName());
            ArrayNode vector = row.putArray(properties.resolveVectorField());
            embeddings.get(i).forEach(vector::add);
            operations.add(row);
        }
        if (operations.size() >= 200) {
            bulkOperations(operations, false);
            operations.clear();
        }
        return batch.size();
    }

    public RagDtos.SyncResult syncContentIndex() {
        RagProperties.Rag rag = properties.getRag();
        AiRagVectorModel chunkModel = vectorModelFromProperties(rag);
        List<AiRagChunkStrategy> chunkStrategies = chunkStrategyService.all();
        List<JsonNode> documents = fetchContentDocuments();
        deleteExistingVectors();

        List<ObjectNode> operations = new ArrayList<>();
        long chunks = 0;
        for (JsonNode document : documents) {
            String id = document.path("_id").asText();
            JsonNode source = document.path("_source");
            String title = source.path("title").asText("");
            String content = source.path("content").asText("");
            String url = source.path("url").asText("");
            String spiderName = source.path("spiderName").asText("");
            long spiderId = source.path("spiderId").asLong(-1);
            ChunkingResult chunking = splitText(documentText(title, content), documentTypes(source),
                    chunkStrategies, chunkModel);
            List<String> parts = chunking.chunks();
            for (int start = 0; start < parts.size(); start += EMBEDDING_BATCH_SIZE) {
                List<String> batch = parts.subList(start,
                        Math.min(start + EMBEDDING_BATCH_SIZE, parts.size()));
                List<List<Double>> embeddings = createEmbeddings(batch);
                for (int i = 0; i < batch.size(); i++) {
                    String chunkId = id + ":" + (start + i);
                    ObjectNode action = objectMapper.createObjectNode();
                    action.set("index", objectMapper.createObjectNode()
                            .put("_index", properties.resolveVectorIndex())
                            .put("_id", chunkId));
                    operations.add(action);

                    ObjectNode row = objectMapper.createObjectNode();
                    row.put("doc_id", id);
                    row.put("chunk_id", chunkId);
                    row.put("title", title);
                    row.put("heading", chunking.headings().get(start + i));
                    row.put("content", batch.get(i));
                    row.put("parent_text", chunking.parentTexts().get(start + i));
                    row.put("url", url);
                    row.put("spiderName", spiderName);
                    row.put("embedding_model", rag.getEmbeddingModel());
                    row.put("chunk_strategy_id", chunking.strategy().getId());
                    row.put("chunk_strategy_name", chunking.strategy().getName());
                    if (spiderId >= 0) row.put("spiderId", spiderId);
                    ArrayNode vector = row.putArray(properties.resolveVectorField());
                    embeddings.get(i).forEach(vector::add);
                    operations.add(row);
                    chunks++;
                }
                if (operations.size() >= 200) {
                    bulkOperations(operations, false);
                    operations.clear();
                }
            }
        }

        if (!operations.isEmpty()) {
            bulkOperations(operations, false);
        }
        refreshIndex(properties.resolveVectorIndex());

        RagDtos.SyncResult result = new RagDtos.SyncResult();
        result.setSourceDocuments(documents.size());
        result.setChunks(chunks);
        result.setVectorDocuments(chunks);
        return result;
    }

    public java.util.List<AiKnowledgeDocumentDtos.Response> listKnowledgeDocuments(Long knowledgeBaseId) {
        if (knowledgeBaseId == null) throw new BizException("请选择知识库");
        if (knowledgeBaseMapper.selectById(knowledgeBaseId) == null)
            throw new BizException("知识库不存在");
        return knowledgeDocumentMapper.selectList(new LambdaQueryWrapper<AiKnowledgeDocument>()
                        .eq(AiKnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId)
                        .orderByDesc(AiKnowledgeDocument::getCreateTime))
                .stream().map(record -> {
                    AiKnowledgeDocumentDtos.Response response = new AiKnowledgeDocumentDtos.Response();
                    response.setId(record.getId());
                    response.setKnowledgeBaseId(record.getKnowledgeBaseId());
                    response.setTitle(record.getTitle());
                    response.setOriginalName(record.getOriginalName());
                    response.setContentType(record.getContentType());
                    response.setFileSize(record.getFileSize());
                    response.setMinioBucket(record.getMinioBucket());
                    response.setObjectName(record.getObjectName());
                    response.setSourceDocumentId(record.getSourceDocumentId());
                    response.setVectorized(record.getVectorized());
                    response.setCreateTime(record.getCreateTime());
                    return response;
                }).toList();
    }

    public AiKnowledgeDocumentDtos.Response uploadKnowledgeDocument(
            Long knowledgeBaseId, MultipartFile file) {
        AiKnowledgeBase base = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (base == null) throw new BizException("知识库不存在");
        if (!"UPLOAD".equalsIgnoreCase(base.getSourceType()))
            throw new BizException("该知识库不是直接上传类型");
        if (file == null || file.isEmpty()) throw new BizException("请选择要上传的文档");

        String originalName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalName)) originalName = "未命名文档";
        String title = originalName.replaceFirst("\\.[^.]+$", "");
        String content;
        try (java.io.InputStream input = file.getInputStream()) {
            content = isMarkdownDocument(originalName, file.getContentType())
                    ? decodeMarkdown(input)
                    : extractTextWithTika(input);
        } catch (Exception e) {
            throw new IllegalStateException("文档解析失败: " + e.getMessage(), e);
        }
        if (!StringUtils.hasText(content)) throw new BizException("文档没有可解析的文本内容");

        String bucket = StringUtils.hasText(uploadBucket)
                ? uploadBucket.trim().toLowerCase(java.util.Locale.ROOT) : "crawler-knowledge";
        String objectName = "knowledge/" + knowledgeBaseId + "/"
                + java.util.UUID.randomUUID().toString().replace("-", "") + "_" + originalName;
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            try (java.io.InputStream input = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket).object(objectName).stream(input, file.getSize(), -1)
                        .contentType(file.getContentType()).build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("文件上传到 MinIO 失败: " + e.getMessage(), e);
        }

        String sourceDocumentId = "upload_" + java.util.UUID.randomUUID().toString().replace("-", "");
        ObjectNode source = objectMapper.createObjectNode();
        source.put("title", title).put("content", content).put("url", objectName)
                .put("originalName", originalName)
                .put("contentType", file.getContentType() == null ? "" : file.getContentType())
                .put("spiderName", "上传文档").put("knowledgeBaseId", knowledgeBaseId);
        try {
            HttpResponse<String> indexed = elasticsearchRequest("PUT",
                    "/" + properties.resolveSourceIndex() + "/_doc/" + sourceDocumentId, toJson(source));
            if (indexed.statusCode() >= 300)
                throw new IllegalStateException("写入知识库源索引失败: " + indexed.statusCode());
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("写入知识库源索引失败: " + e.getMessage(), e);
        }

        AiRagVectorModel model = vectorModelMapper.selectById(base.getVectorModelId());
        if (model == null) throw new BizException("知识库向量模型不存在");
        try {
            vectorizeUploadedDocument(base, model, sourceDocumentId, source);
        } catch (RuntimeException e) {
            throw new IllegalStateException("文档向量化失败: " + e.getMessage(), e);
        }

        AiKnowledgeDocument record = new AiKnowledgeDocument();
        record.setKnowledgeBaseId(knowledgeBaseId);
        record.setTitle(title);
        record.setOriginalName(originalName);
        record.setContentType(file.getContentType() == null ? "" : file.getContentType());
        record.setFileSize(file.getSize());
        record.setMinioBucket(bucket);
        record.setObjectName(objectName);
        record.setSourceDocumentId(sourceDocumentId);
        record.setVectorized(true);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(record.getCreateTime());
        if (knowledgeDocumentMapper.insert(record) != 1)
            throw new IllegalStateException("保存上传文档记录失败");

        AiKnowledgeDocumentDtos.Response response = new AiKnowledgeDocumentDtos.Response();
        response.setId(record.getId());
        response.setKnowledgeBaseId(record.getKnowledgeBaseId());
        response.setTitle(record.getTitle());
        response.setOriginalName(record.getOriginalName());
        response.setContentType(record.getContentType());
        response.setFileSize(record.getFileSize());
        response.setMinioBucket(record.getMinioBucket());
        response.setObjectName(record.getObjectName());
        response.setSourceDocumentId(record.getSourceDocumentId());
        response.setVectorized(record.getVectorized());
        response.setCreateTime(record.getCreateTime());
        return response;
    }

    private int vectorizeUploadedDocument(
            AiKnowledgeBase base, AiRagVectorModel model, String sourceDocumentId, JsonNode source) {
        ensureKnowledgeVectorIndex(base, model);
        String title = source.path("title").asText("");
        String content = source.path("content").asText("");
        List<AiRagChunkStrategy> chunkStrategies = chunkStrategyService.all();
        List<ObjectNode> operations = new ArrayList<>();
        ChunkingResult chunking = splitText(documentText(title, content), documentTypes(source),
                chunkStrategies, model);
        List<String> parts = chunking.chunks();
        for (int start = 0; start < parts.size(); start += EMBEDDING_BATCH_SIZE) {
            List<String> batch = parts.subList(start,
                    Math.min(start + EMBEDDING_BATCH_SIZE, parts.size()));
            List<List<Double>> embeddings = createEmbeddings(batch, model, model.getDimensions());
            for (int i = 0; i < batch.size(); i++) {
                String chunkId = sourceDocumentId + ":" + (start + i);
                operations.add(objectMapper.createObjectNode()
                        .set("index", objectMapper.createObjectNode()
                                .put("_index", resolveKnowledgeVectorIndex(model)).put("_id", chunkId)));
                ObjectNode row = objectMapper.createObjectNode();
                row.put("doc_id", sourceDocumentId).put("chunk_id", chunkId)
                        .put("title", title).put("heading", chunking.headings().get(start + i))
                        .put("content", batch.get(i))
                        .put("parent_text", chunking.parentTexts().get(start + i))
                        .put("url", "").put("spiderName", "上传文档")
                        .put("knowledgeBaseId", base.getId())
                        .put("embedding_model", model.getEmbeddingModel())
                        .put("chunk_strategy_id", chunking.strategy().getId())
                        .put("chunk_strategy_name", chunking.strategy().getName());
                ArrayNode vector = row.putArray(properties.resolveVectorField());
                embeddings.get(i).forEach(vector::add);
                operations.add(row);
            }
        }
        if (!operations.isEmpty()) bulkOperations(operations);
        return parts.size();
    }

    private void ensureKnowledgeVectorIndex(AiKnowledgeBase base, AiRagVectorModel model) {
        String vectorIndex = resolveKnowledgeVectorIndex(model);
        ObjectNode mapping = objectMapper.createObjectNode();
        ObjectNode properties = mapping.putObject("mappings").putObject("properties");
        properties.putObject(this.properties.resolveVectorField())
                .put("type", "dense_vector")
                .put("dims", model.getDimensions())
                .put("index", true)
                .put("similarity", "cosine");
        properties.putObject("doc_id").put("type", "keyword");
        properties.putObject("chunk_id").put("type", "keyword");
        properties.putObject("title").put("type", "text")
                .put("analyzer", "ik_max_word").put("search_analyzer", "ik_smart");
        properties.putObject("heading").put("type", "text")
                .put("analyzer", "ik_max_word").put("search_analyzer", "ik_smart");
        properties.putObject("content").put("type", "text")
                .put("analyzer", "ik_max_word").put("search_analyzer", "ik_smart");
        // 父块文本（small-to-big）：不建倒排索引，仅用于检索后补充上下文
        properties.putObject("parent_text").put("type", "text").put("index", false);
        properties.putObject("url").put("type", "keyword");
        properties.putObject("spiderName").put("type", "keyword");
        properties.putObject("knowledgeBaseId").put("type", "long");
        properties.putObject("embedding_model").put("type", "keyword");
        properties.putObject("chunk_strategy_id").put("type", "long");
        properties.putObject("chunk_strategy_name").put("type", "keyword");
        try {
            HttpResponse<String> existing = elasticsearchRequest("HEAD",
                    "/" + vectorIndex, null);
            if (existing.statusCode() == 404) {
                HttpResponse<String> created = elasticsearchRequest("PUT",
                        "/" + vectorIndex, toJson(mapping));
                if (created.statusCode() >= 300)
                    throw new IllegalStateException("创建知识库向量索引失败: " + created.statusCode());
            } else if (existing.statusCode() >= 300) {
                throw new IllegalStateException("检查知识库向量索引失败: " + existing.statusCode());
            } else {
                HttpResponse<String> updated = elasticsearchRequest("PUT",
                        "/" + vectorIndex + "/_mapping",
                        toJson(mapping.path("mappings")));
                if (updated.statusCode() >= 300)
                    throw new IllegalStateException("更新知识库向量索引映射失败: "
                            + updated.statusCode() + " " + updated.body());
            }
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("初始化知识库向量索引失败: " + e.getMessage(), e);
        }
    }

    public KnowledgeBaseDtos.Stats getKnowledgeBaseStats(Long knowledgeBaseId) {
        if (knowledgeBaseId == null) return getKnowledgeBaseStats();
        AiKnowledgeBase base = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (base == null) throw new BizException("知识库不存在");
        AiRagVectorModel model = vectorModelMapper.selectById(base.getVectorModelId());
        if (model == null) throw new BizException("知识库向量模型不存在");
        ensureKnowledgeVectorIndex(base, model);
        KnowledgeBaseDtos.Stats stats = new KnowledgeBaseDtos.Stats();
        stats.setSourceIndex(properties.resolveSourceIndex());
        String vectorIndex = resolveKnowledgeVectorIndex(model);
        stats.setVectorIndex(vectorIndex);
        List<String> excludedIds = getExcludedDocumentIds(properties.resolveSourceIndex());
        Long ownerKnowledgeBaseId = "UPLOAD".equalsIgnoreCase(base.getSourceType()) ? base.getId() : null;
        stats.setSourceDocuments(countDocuments(properties.resolveSourceIndex(),
                sourceDocumentQuery(null, true, List.of(), ownerKnowledgeBaseId)));
        ObjectNode excludedQuery = excludedDocumentIdsQuery(excludedIds);
        if (ownerKnowledgeBaseId != null && !excludedIds.isEmpty()) {
            ObjectNode bool = objectMapper.createObjectNode();
            bool.putArray("filter").add(knowledgeBaseOwnerQuery(ownerKnowledgeBaseId));
            bool.putArray("must").add(excludedQuery);
            excludedQuery = objectMapper.createObjectNode().set("bool", bool);
        }
        stats.setExcludedDocuments(excludedIds.isEmpty() ? 0
                : countDocuments(properties.resolveSourceIndex(), excludedQuery));
        stats.setIncludedDocuments(Math.max(0,
                stats.getSourceDocuments() - stats.getExcludedDocuments()));
        stats.setVectorDocuments(countVectorDocuments(vectorIndex, base.getId()));
        return stats;
    }

    public KnowledgeBaseDtos.Stats getKnowledgeBaseStats() {
        RagProperties.Rag rag = properties.getRag();
        KnowledgeBaseDtos.Stats stats = new KnowledgeBaseDtos.Stats();
        stats.setSourceIndex(rag.getSourceIndex());
        stats.setVectorIndex(properties.resolveVectorIndex());
        List<String> excludedIds = getExcludedDocumentIds();
        stats.setSourceDocuments(countDocuments(rag.getSourceIndex(), matchAllQuery()));
        stats.setExcludedDocuments(excludedIds.isEmpty() ? 0
                : countDocuments(rag.getSourceIndex(), excludedDocumentIdsQuery(excludedIds)));
        stats.setIncludedDocuments(Math.max(0,
                stats.getSourceDocuments() - stats.getExcludedDocuments()));
        stats.setVectorDocuments(countVectorDocuments(properties.resolveVectorIndex()));
        return stats;
    }

    public KnowledgeBaseDtos.DocumentPage listKnowledgeBaseDocuments(
            Long knowledgeBaseId, int page, int pageSize, String keyword,
            boolean includeExcluded, Boolean vectorized) {
        if (knowledgeBaseId == null) throw new BizException("请选择知识库");
        AiKnowledgeBase base = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (base == null) throw new BizException("知识库不存在");
        if (page < 1 || pageSize < 1 || pageSize > 100 || (long) (page - 1) * pageSize >= 10_000) {
            throw new BizException("分页参数不合法");
        }
        List<String> excludedIds = getExcludedDocumentIds(properties.resolveSourceIndex());
        Long ownerKnowledgeBaseId = "UPLOAD".equalsIgnoreCase(base.getSourceType()) ? base.getId() : null;
        ObjectNode query = sourceDocumentQuery(keyword, includeExcluded, excludedIds, ownerKnowledgeBaseId);
        if (vectorized != null) {
            Set<String> vectorizedDocumentIds = findAllVectorizedDocumentIds(base);
            if (Boolean.TRUE.equals(vectorized) && vectorizedDocumentIds.isEmpty()) {
                KnowledgeBaseDtos.DocumentPage emptyPage = new KnowledgeBaseDtos.DocumentPage();
                emptyPage.setRecords(List.of());
                emptyPage.setTotal(0);
                emptyPage.setPage(page);
                emptyPage.setPageSize(pageSize);
                return emptyPage;
            }
            if (!vectorizedDocumentIds.isEmpty()) {
                ObjectNode bool = objectMapper.createObjectNode();
                bool.putArray("filter").add(query);
                ObjectNode idFilter = objectMapper.createObjectNode().set("terms",
                        objectMapper.createObjectNode()
                                .set("_id", objectMapper.valueToTree(vectorizedDocumentIds)));
                if (Boolean.TRUE.equals(vectorized)) {
                    bool.withArray("filter").add(idFilter);
                } else {
                    bool.putArray("must_not").add(idFilter);
                }
                query = objectMapper.createObjectNode().set("bool", bool);
            }
        }
        ObjectNode body = objectMapper.createObjectNode()
                .put("from", (page - 1) * pageSize)
                .put("size", pageSize);
        body.putArray("sort").add("_doc");
        body.putArray("_source").add("title").add("content").add("url")
                .add("spiderName").add("spiderId");
        body.set("query", query);
        try {
            HttpResponse<String> response = elasticsearchRequest("POST",
                    "/" + properties.resolveSourceIndex() + "/_search", toJson(body));
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("读取知识库文档失败: " + response.statusCode()
                        + " " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode totalNode = root.path("hits").path("total");
            long total = totalNode.isNumber() ? totalNode.asLong() : totalNode.path("value").asLong();
            List<KnowledgeBaseDtos.Document> records = new ArrayList<>();
            for (JsonNode hit : root.path("hits").path("hits")) {
                JsonNode source = hit.path("_source");
                KnowledgeBaseDtos.Document document = new KnowledgeBaseDtos.Document();
                document.setId(hit.path("_id").asText());
                document.setTitle(source.path("title").asText(""));
                document.setSnippet(snippet(source.path("content").asText(""), 240));
                document.setUrl(source.path("url").asText(""));
                document.setSpiderName(source.path("spiderName").asText(""));
                document.setSpiderId(source.path("spiderId").isNumber()
                        ? source.path("spiderId").asLong() : null);
                document.setExcluded(excludedIds.contains(document.getId()));
                records.add(document);
            }
            Map<String, Long> chunkCounts = findVectorChunkCounts(base, records);
            records.forEach(document -> {
                long chunkCount = chunkCounts.getOrDefault(document.getId(), 0L);
                document.setChunkCount(chunkCount);
                document.setVectorized(chunkCount > 0);
            });
            KnowledgeBaseDtos.DocumentPage result = new KnowledgeBaseDtos.DocumentPage();
            result.setRecords(records);
            result.setTotal(total);
            result.setPage(page);
            result.setPageSize(pageSize);
            return result;
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("读取知识库文档失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Long> findVectorChunkCounts(
            AiKnowledgeBase base, List<KnowledgeBaseDtos.Document> documents) {
        if (documents.isEmpty()) return Map.of();
        AiRagVectorModel model = vectorModelMapper.selectById(base.getVectorModelId());
        if (model == null) throw new BizException("知识库向量模型不存在");

        ArrayNode documentIds = objectMapper.createArrayNode();
        documents.forEach(document -> documentIds.add(document.getId()));
        ObjectNode filters = objectMapper.createObjectNode();
        filters.putArray("filter")
                .add(objectMapper.createObjectNode().set("terms",
                        objectMapper.createObjectNode().set("doc_id", documentIds)))
                .add(knowledgeBaseOwnerQuery(base.getId()));
        ObjectNode body = objectMapper.createObjectNode().put("size", 0);
        body.set("query", objectMapper.createObjectNode().set("bool", filters));
        body.putObject("aggs").putObject("documents").putObject("terms")
                .put("field", "doc_id").put("size", documents.size());

        String vectorIndex = resolveKnowledgeVectorIndex(model);
        try {
            HttpResponse<String> response = elasticsearchRequest(
                    "POST", "/" + vectorIndex + "/_search", toJson(body));
            if (response.statusCode() == 404) return Map.of();
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("读取文档分块数量失败: " + response.statusCode()
                        + " " + response.body());
            }
            JsonNode buckets = objectMapper.readTree(response.body())
                    .path("aggregations").path("documents").path("buckets");
            Map<String, Long> chunkCounts = new java.util.HashMap<>();
            for (JsonNode bucket : buckets) {
                chunkCounts.put(bucket.path("key").asText(), bucket.path("doc_count").asLong());
            }
            return chunkCounts;
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("读取文档分块数量失败: " + e.getMessage(), e);
        }
    }

    private Set<String> findAllVectorizedDocumentIds(AiKnowledgeBase base) {
        AiRagVectorModel model = vectorModelMapper.selectById(base.getVectorModelId());
        if (model == null) throw new BizException("知识库向量模型不存在");
        String vectorIndex = resolveKnowledgeVectorIndex(model);
        Set<String> documentIds = new HashSet<>();
        ObjectNode body = objectMapper.createObjectNode().put("size", 0);
        body.putObject("query").set("term",
                objectMapper.createObjectNode().put("knowledgeBaseId", base.getId()));
        ObjectNode composite = body.putObject("aggs").putObject("documents")
                .putObject("composite").put("size", 1000);
        composite.putArray("sources").addObject()
                .putObject("document_id").putObject("terms").put("field", "doc_id");

        try {
            while (true) {
                HttpResponse<String> response = elasticsearchRequest(
                        "POST", "/" + vectorIndex + "/_search", toJson(body));
                if (response.statusCode() == 404) return Set.of();
                if (response.statusCode() >= 300) {
                    throw new IllegalStateException("读取文档向量状态失败: " + response.statusCode()
                            + " " + response.body());
                }
                JsonNode aggregations = objectMapper.readTree(response.body())
                        .path("aggregations").path("documents");
                JsonNode buckets = aggregations.path("buckets");
                for (JsonNode bucket : buckets) {
                    documentIds.add(bucket.path("key").path("document_id").asText());
                }
                JsonNode afterKey = aggregations.path("after_key");
                if (!afterKey.isObject() || buckets.isEmpty()) break;
                composite.set("after", afterKey.deepCopy());
            }
            return documentIds;
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("读取文档向量状态失败: " + e.getMessage(), e);
        }
    }

    public KnowledgeBaseDtos.DocumentPage listKnowledgeBaseDocuments(
            int page, int pageSize, String keyword, boolean includeExcluded) {
        if (page < 1 || pageSize < 1 || pageSize > 100 || (long) (page - 1) * pageSize >= 10_000) {
            throw new BizException("分页参数不合法");
        }
        List<String> excludedIds = getExcludedDocumentIds();
        ObjectNode query = sourceDocumentQuery(keyword, includeExcluded, excludedIds, null);
        ObjectNode body = objectMapper.createObjectNode()
                .put("from", (page - 1) * pageSize)
                .put("size", pageSize);
        body.putArray("sort").add("_doc");
        body.putArray("_source").add("title").add("content").add("url")
                .add("spiderName").add("spiderId");
        body.set("query", query);
        try {
            HttpResponse<String> response = elasticsearchRequest("POST",
                    "/" + properties.resolveSourceIndex() + "/_search", toJson(body));
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("读取知识库文档失败: " + response.statusCode()
                        + " " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode totalNode = root.path("hits").path("total");
            long total = totalNode.isNumber() ? totalNode.asLong() : totalNode.path("value").asLong();
            List<KnowledgeBaseDtos.Document> records = new ArrayList<>();
            for (JsonNode hit : root.path("hits").path("hits")) {
                JsonNode source = hit.path("_source");
                KnowledgeBaseDtos.Document document = new KnowledgeBaseDtos.Document();
                document.setId(hit.path("_id").asText());
                document.setTitle(source.path("title").asText(""));
                document.setSnippet(snippet(source.path("content").asText(""), 240));
                document.setUrl(source.path("url").asText(""));
                document.setSpiderName(source.path("spiderName").asText(""));
                document.setSpiderId(source.path("spiderId").isNumber()
                        ? source.path("spiderId").asLong() : null);
                document.setExcluded(excludedIds.contains(document.getId()));
                records.add(document);
            }
            KnowledgeBaseDtos.DocumentPage result = new KnowledgeBaseDtos.DocumentPage();
            result.setRecords(records);
            result.setTotal(total);
            result.setPage(page);
            result.setPageSize(pageSize);
            return result;
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("读取知识库文档失败: " + e.getMessage(), e);
        }
    }

    private List<KnowledgeBaseDtos.Document> sampleRandomKnowledgeBaseDocuments(
            Long knowledgeBaseId, int sampleSize) {
        if (knowledgeBaseId == null) throw new BizException("请选择知识库");
        AiKnowledgeBase base = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (base == null) throw new BizException("知识库不存在");

        List<String> excludedIds = getExcludedDocumentIds(properties.resolveSourceIndex());
        Long ownerKnowledgeBaseId = "UPLOAD".equalsIgnoreCase(base.getSourceType())
                ? base.getId() : null;
        ObjectNode randomQuery = objectMapper.createObjectNode();
        randomQuery.set("query", sourceDocumentQuery(null, false, excludedIds, ownerKnowledgeBaseId));
        randomQuery.putObject("random_score");
        ObjectNode body = objectMapper.createObjectNode().put("size", sampleSize);
        body.putArray("sort").addObject().put("_score", "desc");
        body.putArray("_source").add("title").add("content");
        body.set("query", objectMapper.createObjectNode().set("function_score", randomQuery));

        try {
            HttpResponse<String> response = elasticsearchRequest(
                    "POST", "/" + properties.resolveSourceIndex() + "/_search", toJson(body));
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("随机读取知识库文档失败: " + response.statusCode()
                        + " " + response.body());
            }
            List<KnowledgeBaseDtos.Document> documents = new ArrayList<>();
            JsonNode root = objectMapper.readTree(response.body());
            for (JsonNode hit : root.path("hits").path("hits")) {
                JsonNode source = hit.path("_source");
                KnowledgeBaseDtos.Document document = new KnowledgeBaseDtos.Document();
                document.setId(hit.path("_id").asText());
                document.setTitle(source.path("title").asText(""));
                document.setSnippet(snippet(source.path("content").asText(""), 240));
                documents.add(document);
            }
            return documents;
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("随机读取知识库文档失败: " + e.getMessage(), e);
        }
    }

    public KnowledgeBaseDtos.DocumentDetail getKnowledgeBaseDocument(
            Long knowledgeBaseId, String documentId) {
        if (knowledgeBaseId == null) return getKnowledgeBaseDocument(documentId);
        AiKnowledgeBase base = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (base == null) throw new BizException("知识库不存在");
        JsonNode hit = findSourceDocument(properties.resolveSourceIndex(), documentId,
                "title", "content", "url", "spiderName", "spiderId", "images");
        JsonNode source = hit.path("_source");
        KnowledgeBaseDtos.DocumentDetail detail = new KnowledgeBaseDtos.DocumentDetail();
        detail.setId(hit.path("_id").asText());
        detail.setTitle(source.path("title").asText(""));
        detail.setContent(source.path("content").asText(""));
        detail.setUrl(source.path("url").asText(""));
        detail.setSpiderName(source.path("spiderName").asText(""));
        detail.setSpiderId(source.path("spiderId").isNumber()
                ? source.path("spiderId").asLong() : null);
        detail.setImages(readDocumentImages(source));
        detail.setExcluded(excludedDocumentMapper.selectCount(
                new LambdaQueryWrapper<AiRagExcludedDocument>()
                        .eq(AiRagExcludedDocument::getSourceIndex, properties.resolveSourceIndex())
                        .eq(AiRagExcludedDocument::getDocumentId, documentId)) > 0);
        return detail;
    }

    public KnowledgeBaseDtos.DocumentDetail getKnowledgeBaseDocument(String documentId) {
        JsonNode hit = findSourceDocument(properties.resolveSourceIndex(), documentId,
                "title", "content", "url", "spiderName", "spiderId", "images");
        JsonNode source = hit.path("_source");
        KnowledgeBaseDtos.DocumentDetail detail = new KnowledgeBaseDtos.DocumentDetail();
        detail.setId(hit.path("_id").asText());
        detail.setTitle(source.path("title").asText(""));
        detail.setContent(source.path("content").asText(""));
        detail.setUrl(source.path("url").asText(""));
        detail.setSpiderName(source.path("spiderName").asText(""));
        detail.setSpiderId(source.path("spiderId").isNumber()
                ? source.path("spiderId").asLong() : null);
        detail.setImages(readDocumentImages(source));
        detail.setExcluded(isDocumentExcluded(documentId));
        return detail;
    }

    private List<String> readDocumentImages(JsonNode source) {
        List<String> images = new ArrayList<>();
        JsonNode imageNodes = source.path("images");
        if (imageNodes.isArray()) {
            for (JsonNode image : imageNodes) {
                if (image.isTextual() && StringUtils.hasText(image.asText())) {
                    images.add(image.asText());
                }
            }
        }
        return images;
    }

    public java.util.List<KnowledgeBaseDtos.VectorChunk> getKnowledgeBaseDocumentChunks(
            Long knowledgeBaseId, String documentId) {
        String vectorIndex;
        if (knowledgeBaseId == null) {
            vectorIndex = properties.resolveVectorIndex();
        } else {
            AiKnowledgeBase base = knowledgeBaseMapper.selectById(knowledgeBaseId);
            if (base == null) throw new BizException("知识库不存在");
            vectorIndex = resolveKnowledgeVectorIndex(base);
        }
        ObjectNode body = objectMapper.createObjectNode()
                .put("size", 10000)
                .put("track_total_hits", false);
        body.putArray("sort").add("_doc");
        body.set("query", objectMapper.createObjectNode()
                .set("term", objectMapper.createObjectNode().put("doc_id", documentId)));
        body.set("_source", objectMapper.createArrayNode()
                .add("chunk_id").add("title").add("heading").add("content").add("embedding_model")
                .add("chunk_strategy_id").add("chunk_strategy_name"));
        try {
            HttpResponse<String> response = elasticsearchRequest("POST",
                    "/" + vectorIndex + "/_search", toJson(body));
            if (response.statusCode() == 404) return java.util.List.of();
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("读取文档向量分块失败: " + response.statusCode()
                        + " " + response.body());
            }
            java.util.List<KnowledgeBaseDtos.VectorChunk> chunks = new java.util.ArrayList<>();
            JsonNode root = objectMapper.readTree(response.body());
            for (JsonNode hit : root.path("hits").path("hits")) {
                JsonNode source = hit.path("_source");
                String chunkId = source.path("chunk_id").asText(hit.path("_id").asText());
                String content = source.path("content").asText("");
                KnowledgeBaseDtos.VectorChunk chunk = new KnowledgeBaseDtos.VectorChunk();
                chunk.setChunkId(chunkId);
                chunk.setIndex(parseChunkIndex(chunkId));
                chunk.setTitle(source.path("title").asText(""));
                chunk.setHeading(source.path("heading").asText(""));
                chunk.setContent(content);
                chunk.setCharacterCount(content.length());
                chunk.setEmbeddingModel(source.path("embedding_model").asText(""));
                chunk.setChunkStrategyId(source.path("chunk_strategy_id").asText(""));
                chunk.setChunkStrategyName(source.path("chunk_strategy_name").asText(""));
                chunks.add(chunk);
            }
            chunks.sort(java.util.Comparator.comparingInt(KnowledgeBaseDtos.VectorChunk::getIndex));
            return chunks;
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("读取文档向量分块失败: " + e.getMessage(), e);
        }
    }

    public long getKnowledgeBaseDocumentChunkCount(Long knowledgeBaseId, String documentId) {
        String vectorIndex;
        if (knowledgeBaseId == null) {
            vectorIndex = properties.resolveVectorIndex();
        } else {
            AiKnowledgeBase base = knowledgeBaseMapper.selectById(knowledgeBaseId);
            if (base == null) throw new BizException("知识库不存在");
            vectorIndex = resolveKnowledgeVectorIndex(base);
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.set("query", objectMapper.createObjectNode()
                .set("term", objectMapper.createObjectNode().put("doc_id", documentId)));
        try {
            HttpResponse<String> response = elasticsearchRequest(
                    "POST", "/" + vectorIndex + "/_count", toJson(body));
            if (response.statusCode() == 404) return 0;
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("读取文档分块数量失败: " + response.statusCode()
                        + " " + response.body());
            }
            return objectMapper.readTree(response.body()).path("count").asLong();
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("读取文档分块数量失败: " + e.getMessage(), e);
        }
    }

    public java.util.List<KnowledgeBaseDtos.VectorChunk> getKnowledgeBaseDocumentChunk(
            Long knowledgeBaseId, String documentId, String chunkId) {
        if (!chunkId.startsWith(documentId + ":")
                || parseChunkIndex(chunkId) == Integer.MAX_VALUE) {
            throw new BizException("文档分块 ID 不合法");
        }
        String vectorIndex;
        if (knowledgeBaseId == null) {
            vectorIndex = properties.resolveVectorIndex();
        } else {
            AiKnowledgeBase base = knowledgeBaseMapper.selectById(knowledgeBaseId);
            if (base == null) throw new BizException("知识库不存在");
            vectorIndex = resolveKnowledgeVectorIndex(base);
        }

        String encodedChunkId = URLEncoder.encode(chunkId, StandardCharsets.UTF_8)
                .replace("+", "%20");
        try {
            HttpResponse<String> response = elasticsearchRequest(
                    "GET", "/" + vectorIndex + "/_doc/" + encodedChunkId, null);
            if (response.statusCode() == 404) return java.util.List.of();
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("读取文档向量分块失败: " + response.statusCode()
                        + " " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode source = root.path("_source");
            if (!documentId.equals(source.path("doc_id").asText())) {
                return java.util.List.of();
            }
            String content = source.path("content").asText("");
            KnowledgeBaseDtos.VectorChunk chunk = new KnowledgeBaseDtos.VectorChunk();
            chunk.setChunkId(source.path("chunk_id").asText(chunkId));
            chunk.setIndex(parseChunkIndex(chunk.getChunkId()));
            chunk.setTitle(source.path("title").asText(""));
            chunk.setHeading(source.path("heading").asText(""));
            chunk.setContent(content);
            chunk.setCharacterCount(content.length());
            chunk.setEmbeddingModel(source.path("embedding_model").asText(""));
            chunk.setChunkStrategyId(source.path("chunk_strategy_id").asText(""));
            chunk.setChunkStrategyName(source.path("chunk_strategy_name").asText(""));
            return java.util.List.of(chunk);
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("读取文档向量分块失败: " + e.getMessage(), e);
        }
    }

    private int parseChunkIndex(String chunkId) {
        int separator = chunkId.lastIndexOf(':');
        if (separator >= 0) {
            try {
                return Integer.parseInt(chunkId.substring(separator + 1));
            } catch (NumberFormatException ignored) {
                // Keep the original ES hit order when the chunk ID is not numeric.
            }
        }
        return Integer.MAX_VALUE;
    }

    public RagDtos.DocumentVectorizationResult vectorizeDocument(
            Long knowledgeBaseId, String documentId) {
        if (knowledgeBaseId == null) return vectorizeDocument(documentId);
        AiKnowledgeBase base = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (base == null) throw new BizException("知识库不存在");
        AiRagVectorModel model = vectorModelMapper.selectById(base.getVectorModelId());
        if (model == null) throw new BizException("知识库向量模型不存在");
        JsonNode hit = findSourceDocument(properties.resolveSourceIndex(), documentId);
        JsonNode source = hit.path("_source");
        int chunks = vectorizeUploadedDocument(base, model, documentId, source);
        setDocumentExcluded(properties.resolveSourceIndex(), documentId, false);
        RagDtos.DocumentVectorizationResult result = new RagDtos.DocumentVectorizationResult();
        result.setDocumentId(documentId);
        result.setChunks(chunks);
        return result;
    }

    public RagDtos.DocumentVectorizationResult vectorizeDocument(String documentId) {
        JsonNode hit = findSourceDocument(documentId);
        JsonNode source = hit.path("_source");
        List<ObjectNode> operations = createDocumentVectorOperations(documentId, source);
        if (operations.isEmpty()) {
            throw new BizException("文档没有可向量化的标题或正文内容");
        }
        for (int i = 0; i < operations.size(); i += 200) {
            bulkOperations(operations.subList(i, Math.min(i + 200, operations.size())));
        }
        deleteDocumentVectorsExcept(documentId, chunkIds(operations));
        setDocumentExcluded(documentId, false);

        RagDtos.DocumentVectorizationResult result = new RagDtos.DocumentVectorizationResult();
        result.setDocumentId(documentId);
        result.setChunks(operations.size() / 2);
        return result;
    }

    public void excludeKnowledgeBaseDocument(Long knowledgeBaseId, String documentId) {
        if (knowledgeBaseId == null) {
            excludeKnowledgeBaseDocument(documentId);
            return;
        }
        AiKnowledgeBase base = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (base == null) throw new BizException("知识库不存在");
        findSourceDocument(properties.resolveSourceIndex(), documentId);
        deleteDocumentVectorsExcept(resolveKnowledgeVectorIndex(base), documentId, List.of());
        setDocumentExcluded(properties.resolveSourceIndex(), documentId, true);
    }

    public void excludeKnowledgeBaseDocument(String documentId) {
        findSourceDocument(documentId);
        deleteDocumentVectorsExcept(documentId, List.of());
        setDocumentExcluded(documentId, true);
    }

    private List<ObjectNode> createDocumentVectorOperations(String documentId, JsonNode source) {
        RagProperties.Rag rag = properties.getRag();
        AiRagVectorModel chunkModel = vectorModelFromProperties(rag);
        String title = source.path("title").asText("");
        String content = source.path("content").asText("");
        String text = documentText(title, content);
        ChunkingResult chunking = splitText(text, documentTypes(source),
                chunkStrategyService.all(), chunkModel);
        List<String> chunks = chunking.chunks();
        List<ObjectNode> operations = new ArrayList<>(chunks.size() * 2);
        for (int start = 0; start < chunks.size(); start += EMBEDDING_BATCH_SIZE) {
            List<String> batch = chunks.subList(start,
                    Math.min(start + EMBEDDING_BATCH_SIZE, chunks.size()));
            List<List<Double>> embeddings = createEmbeddings(batch);
            for (int i = 0; i < batch.size(); i++) {
                String chunkId = documentId + ":" + (start + i);
                ObjectNode action = objectMapper.createObjectNode();
                action.set("index", objectMapper.createObjectNode()
                        .put("_index", properties.resolveVectorIndex())
                        .put("_id", chunkId));
                operations.add(action);

                ObjectNode row = objectMapper.createObjectNode();
                row.put("doc_id", documentId);
                row.put("chunk_id", chunkId);
                row.put("title", title);
                row.put("heading", chunking.headings().get(start + i));
                row.put("content", batch.get(i));
                row.put("parent_text", chunking.parentTexts().get(start + i));
                row.put("url", source.path("url").asText(""));
                row.put("spiderName", source.path("spiderName").asText(""));
                row.put("embedding_model", properties.getRag().getEmbeddingModel());
                row.put("chunk_strategy_id", chunking.strategy().getId());
                row.put("chunk_strategy_name", chunking.strategy().getName());
                if (source.path("spiderId").isNumber()) {
                    row.put("spiderId", source.path("spiderId").asLong());
                }
                ArrayNode vector = row.putArray(properties.resolveVectorField());
                embeddings.get(i).forEach(vector::add);
                operations.add(row);
            }
        }
        return operations;
    }

    private List<String> chunkIds(List<ObjectNode> operations) {
        List<String> chunkIds = new ArrayList<>(operations.size() / 2);
        for (int i = 0; i < operations.size(); i += 2) {
            chunkIds.add(operations.get(i).path("index").path("_id").asText());
        }
        return chunkIds;
    }

    private void deleteDocumentVectorsExcept(String documentId, List<String> chunkIds) {
        deleteDocumentVectorsExcept(properties.resolveVectorIndex(), documentId, chunkIds);
    }

    private String resolveKnowledgeVectorIndex(AiKnowledgeBase base) {
        AiRagVectorModel model = vectorModelMapper.selectById(base.getVectorModelId());
        if (model == null) throw new BizException("知识库向量模型不存在");
        return resolveKnowledgeVectorIndex(model);
    }

    private String resolveKnowledgeVectorIndex(AiRagVectorModel model) {
        if (model == null) throw new BizException("知识库向量模型不存在");
        return properties.resolveVectorIndex(model.getEmbeddingModel());
    }

    private String resolveKnowledgeVectorIndexes(List<AiKnowledgeBase> knowledgeBases) {
        if (knowledgeBases == null || knowledgeBases.isEmpty()) {
            return properties.resolveVectorIndex();
        }
        return String.join(",", knowledgeBases.stream()
                .map(this::resolveKnowledgeVectorIndex)
                .distinct()
                .toList());
    }

    private void deleteKnowledgeVectors(String vectorIndex, Long knowledgeBaseId) {
        ObjectNode body = objectMapper.createObjectNode()
                .set("query", objectMapper.createObjectNode()
                        .set("term", objectMapper.createObjectNode()
                                .put("knowledgeBaseId", knowledgeBaseId)));
        try {
            HttpResponse<String> response = elasticsearchRequest(
                    "POST",
                    "/" + vectorIndex + "/_delete_by_query?refresh=true&conflicts=proceed",
                    toJson(body));
            if (response.statusCode() >= 300 && response.statusCode() != 404) {
                throw new IllegalStateException("删除知识库旧向量数据失败: " + response.statusCode()
                        + " " + response.body());
            }
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("删除知识库旧向量数据失败: " + e.getMessage(), e);
        }
    }

    private void deleteDocumentVectorsExcept(
            String vectorIndex, String documentId, List<String> chunkIds) {
        ObjectNode bool = objectMapper.createObjectNode();
        bool.putArray("filter").add(objectMapper.createObjectNode()
                .set("term", objectMapper.createObjectNode().put("doc_id", documentId)));
        if (!chunkIds.isEmpty()) {
            bool.putArray("must_not").add(objectMapper.createObjectNode()
                    .set("terms", objectMapper.createObjectNode()
                            .set("chunk_id", objectMapper.valueToTree(chunkIds))));
        }
        ObjectNode body = objectMapper.createObjectNode()
                .set("query", objectMapper.createObjectNode().set("bool", bool));
        try {
            HttpResponse<String> response = elasticsearchRequest("POST",
                    "/" + vectorIndex
                            + "/_delete_by_query?refresh=true&conflicts=proceed",
                    toJson(body));
            if (response.statusCode() >= 300 && response.statusCode() != 404) {
                throw new IllegalStateException("清理文档向量失败: " + response.statusCode()
                        + " " + response.body());
            }
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("清理文档向量失败: " + e.getMessage(), e);
        }
    }

    private void setDocumentExcluded(String documentId, boolean excluded) {
        setDocumentExcluded(properties.resolveSourceIndex(), documentId, excluded);
    }

    private void setDocumentExcluded(
            String sourceIndex, String documentId, boolean excluded) {
        LambdaQueryWrapper<AiRagExcludedDocument> query = new LambdaQueryWrapper<AiRagExcludedDocument>()
                .eq(AiRagExcludedDocument::getSourceIndex, sourceIndex)
                .eq(AiRagExcludedDocument::getDocumentId, documentId);
        if (excluded) {
            if (excludedDocumentMapper.selectCount(query) > 0) return;
            AiRagExcludedDocument record = new AiRagExcludedDocument();
            record.setSourceIndex(sourceIndex);
            record.setDocumentId(documentId);
            record.setCreateTime(LocalDateTime.now());
            try {
                if (excludedDocumentMapper.insert(record) != 1) {
                    throw new IllegalStateException("保存知识库排除状态失败");
                }
            } catch (DuplicateKeyException ignored) {
                if (!isDocumentExcluded(documentId)) {
                    throw new IllegalStateException("保存知识库排除状态失败");
                }
            }
        } else {
            excludedDocumentMapper.delete(query);
        }
    }

    private boolean isDocumentExcluded(String documentId) {
        return excludedDocumentMapper.selectCount(new LambdaQueryWrapper<AiRagExcludedDocument>()
                .eq(AiRagExcludedDocument::getSourceIndex, properties.resolveSourceIndex())
                .eq(AiRagExcludedDocument::getDocumentId, documentId)) > 0;
    }

    private List<String> getExcludedDocumentIds() {
        return getExcludedDocumentIds(properties.resolveSourceIndex());
    }

    private List<String> getExcludedDocumentIds(String sourceIndex) {
        return excludedDocumentMapper.selectList(new LambdaQueryWrapper<AiRagExcludedDocument>()
                        .eq(AiRagExcludedDocument::getSourceIndex, sourceIndex)
                        .select(AiRagExcludedDocument::getDocumentId))
                .stream().map(AiRagExcludedDocument::getDocumentId).toList();
    }

    private JsonNode findSourceDocument(String documentId) {
        return findSourceDocument(properties.resolveSourceIndex(), documentId);
    }

    private JsonNode findSourceDocument(String sourceIndex, String documentId) {
        return findSourceDocument(sourceIndex, documentId, new String[0]);
    }

    private JsonNode findSourceDocument(String sourceIndex, String documentId, String... sourceFields) {
        if (documentId == null || documentId.isBlank()) {
            throw new BizException("文档 ID 不能为空");
        }
        String encodedDocumentId = URLEncoder.encode(documentId, StandardCharsets.UTF_8)
                .replace("+", "%20");
        String path = "/" + sourceIndex + "/_doc/" + encodedDocumentId;
        if (sourceFields.length > 0) {
            path += "?_source_includes=" + String.join(",", sourceFields);
        }
        try {
            HttpResponse<String> response = elasticsearchRequest("GET", path, null);
            if (response.statusCode() == 404) {
                throw new BizException("爬虫源文档不存在或已被移除");
            }
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("读取爬虫源文档失败: " + response.statusCode()
                        + " " + response.body());
            }
            JsonNode hit = objectMapper.readTree(response.body());
            if (!hit.path("found").asBoolean(false)) {
                throw new BizException("爬虫源文档不存在或已被移除");
            }
            return hit;
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            if (e instanceof BizException business) throw business;
            throw new IllegalStateException("读取爬虫源文档失败: " + e.getMessage(), e);
        }
    }

    private long countDocuments(String index, ObjectNode query) {
        ObjectNode body = objectMapper.createObjectNode().set("query", query);
        try {
            HttpResponse<String> response = elasticsearchRequest("POST", "/" + index + "/_count", toJson(body));
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("统计知识库文档失败: " + response.statusCode()
                        + " " + response.body());
            }
            return objectMapper.readTree(response.body()).path("count").asLong();
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("统计知识库文档失败: " + e.getMessage(), e);
        }
    }

    private long countVectorDocuments(String index) {
        return countVectorDocuments(index, null);
    }

    private long countVectorDocuments(String index, Long knowledgeBaseId) {
        ObjectNode query = knowledgeBaseId == null
                ? matchAllQuery()
                : objectMapper.createObjectNode().set("term",
                        objectMapper.createObjectNode().put("knowledgeBaseId", knowledgeBaseId));
        ObjectNode body = objectMapper.createObjectNode().set("query", query);
        try {
            HttpResponse<String> response = elasticsearchRequest("POST", "/" + index + "/_count", toJson(body));
            if (response.statusCode() == 404) return 0;
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("统计知识库向量文档失败: " + response.statusCode()
                        + " " + response.body());
            }
            return objectMapper.readTree(response.body()).path("count").asLong();
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("统计知识库向量文档失败: " + e.getMessage(), e);
        }
    }

    private ObjectNode sourceDocumentQuery(
            String keyword, boolean includeExcluded, List<String> excludedIds,
            Long ownerKnowledgeBaseId) {
        ObjectNode bool = objectMapper.createObjectNode();
        if (ownerKnowledgeBaseId != null) {
            bool.putArray("filter").add(knowledgeBaseOwnerQuery(ownerKnowledgeBaseId));
        }
        if (keyword != null && !keyword.isBlank()) {
            ObjectNode multiMatch = objectMapper.createObjectNode().put("query", keyword.trim());
            multiMatch.putArray("fields").add("title^3").add("content").add("url").add("spiderName");
            bool.putArray("must").add(objectMapper.createObjectNode()
                    .set("multi_match", multiMatch));
        }
        if (!includeExcluded && !excludedIds.isEmpty()) {
            bool.putArray("must_not").add(excludedDocumentIdsQuery(excludedIds));
        }
        return bool.isEmpty() ? matchAllQuery()
                : objectMapper.createObjectNode().set("bool", bool);
    }

    private ObjectNode knowledgeBaseOwnerQuery(Long knowledgeBaseId) {
        return objectMapper.createObjectNode().set("term",
                objectMapper.createObjectNode().put("knowledgeBaseId", knowledgeBaseId));
    }

    private ObjectNode excludedDocumentIdsQuery(List<String> excludedIds) {
        return objectMapper.createObjectNode().set("terms",
                objectMapper.createObjectNode().set("_id", objectMapper.valueToTree(excludedIds)));
    }

    private ObjectNode matchAllQuery() {
        return objectMapper.createObjectNode().set("match_all", objectMapper.createObjectNode());
    }

    private static String snippet(String content, int maxLength) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength
                ? normalized : normalized.substring(0, maxLength) + "…";
    }

    public RagDtos.QueryResponse query(RagDtos.QueryRequest request) {
        return query(request, isLoggedIn(), List.of(), null, null);
    }

    public List<String> generateSuggestedQuestions(
            AiAssistantDtos.Response assistant, Long chatModelId) {
        RagProperties.Rag rag = properties.getRag().copy();
        if (chatModelId != null) {
            AiRagChatModel chatModel = chatModelMapper.selectById(chatModelId);
            if (chatModel == null) throw new BizException("聊天模型不存在");
            rag.setChatProvider(chatModel.getChatProvider());
            rag.setChatBaseUrl(chatModel.getChatBaseUrl());
            rag.setApiKey(chatModel.getApiKey());
            rag.setChatModel(chatModel.getChatModel());
            rag.setContextLength(chatModel.getContextLength() == null ? 8192 : chatModel.getContextLength());
        }

        ObjectNode profile = objectMapper.createObjectNode();
        if (assistant != null) {
            profile.put("name", assistant.getName());
            profile.put("description", assistant.getDescription());
            profile.put("welcomeMessage", assistant.getWelcomeMessage());
            profile.put("systemPrompt", assistant.getSystemPrompt());
            ArrayNode knowledgeBases = profile.putArray("knowledgeBases");
            if (assistant.getKnowledgeBaseNames() != null) {
                assistant.getKnowledgeBaseNames().forEach(knowledgeBases::add);
            }
            ArrayNode knowledgeSamples = profile.putArray("knowledgeBaseSamples");
            if (assistant.getKnowledgeBaseIds() != null) {
                for (Long knowledgeBaseId : assistant.getKnowledgeBaseIds().stream().limit(5).toList()) {
                    try {
                        List<KnowledgeBaseDtos.Document> documents =
                                sampleRandomKnowledgeBaseDocuments(knowledgeBaseId, 3);
                        ObjectNode sample = knowledgeSamples.addObject()
                                .put("knowledgeBaseId", knowledgeBaseId);
                        ArrayNode documentSamples = sample.putArray("documents");
                        for (KnowledgeBaseDtos.Document document : documents) {
                            documentSamples.addObject()
                                    .put("title", document.getTitle())
                                    .put("snippet", document.getSnippet());
                        }
                    } catch (RuntimeException e) {
                        log.warn("读取推荐问题知识库样本失败: assistantId={}, knowledgeBaseId={}",
                                assistant.getId(), knowledgeBaseId, e);
                    }
                }
            }
        }

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", rag.getChatModel());
            ArrayNode messages = body.putArray("messages");
            messages.addObject()
                    .put("role", "system")
                    .put("content", "根据用户提供的智能体和知识库信息，生成 3 个适合用户点击提问的简短中文问题。"
                            + "问题应具体、彼此不同，并贴合智能体用途。只输出合法 JSON 数组，数组元素为字符串；不要输出 Markdown 或解释。");
            messages.addObject()
                    .put("role", "user")
                    .put("content", "智能体信息：\n" + profile);
            if ("ollama".equalsIgnoreCase(rag.getChatProvider())) {
                body.put("stream", false);
                body.putObject("options").put("num_ctx", rag.getContextLength()).put("num_predict", 500);
            } else {
                body.put("temperature", 0.7);
                body.put("max_tokens", 500);
            }

            String path = "ollama".equalsIgnoreCase(rag.getChatProvider())
                    ? "/api/chat"
                    : "/chat/completions";
            HttpResponse<String> response = openAiRequest(path, body, rag);
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("聊天模型返回错误状态: " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            boolean ollama = "ollama".equalsIgnoreCase(rag.getChatProvider());
            JsonNode contentNode = ollama
                    ? root.path("message").path("content")
                    : root.path("choices").path(0).path("message").path("content");
            String content = readChatContent(contentNode);
            String json = content.trim();
            int arrayStart = json.indexOf('[');
            int arrayEnd = json.lastIndexOf(']');
            if (arrayStart >= 0 && arrayEnd > arrayStart) {
                json = json.substring(arrayStart, arrayEnd + 1);
            } else {
                json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
            }
            List<String> result = new ArrayList<>();
            if (json.startsWith("[") || json.startsWith("{")) {
                try {
                    JsonNode generated = objectMapper.readTree(json);
                    JsonNode questions = generated.isArray()
                            ? generated
                            : firstArray(generated, "questions", "suggested_questions", "suggestions", "items");
                    if (questions.isArray()) {
                        for (JsonNode question : questions) {
                            String value = question.isTextual()
                                    ? question.asText()
                                    : firstSuggestedText(question, "question", "text", "content");
                            addSuggestedQuestion(result, value);
                            if (result.size() == 3) break;
                        }
                    }
                } catch (Exception parseException) {
                    log.warn("解析聊天模型推荐问题JSON失败，尝试提取已生成的完整问题: assistantId={}, model={}",
                            assistant == null ? null : assistant.getId(),
                            safeLogValue(rag.getChatModel(), 128));
                    extractCompleteJsonStrings(json, result);
                }
            }
            if (result.isEmpty()) {
                for (String line : content.split("\\R")) {
                    String trimmedLine = line.trim();
                    boolean listItem = trimmedLine.matches("^(?:[-*•]\\s*|\\d+[.)、．]\\s*).+");
                    String value = trimmedLine
                            .replaceFirst("^(?:[-*•]\\s*|\\d+[.)、．]\\s*)", "")
                            .replaceAll("^[\"'“”‘’]+|[\"'“”‘’]+$", "")
                            .trim();
                    if (listItem || value.contains("?") || value.contains("？")) {
                        addSuggestedQuestion(result, value);
                        if (result.size() == 3) break;
                    }
                }
            }
            if (result.isEmpty()) {
                log.warn("聊天模型未返回有效推荐问题: assistantId={}, model={}",
                        assistant == null ? null : assistant.getId(), safeLogValue(rag.getChatModel(), 128));
                return List.of();
            }
            return result;
        } catch (Exception e) {
            log.warn("生成聊天默认问题失败: assistantId={}, model={}",
                    assistant == null ? null : assistant.getId(), safeLogValue(rag.getChatModel(), 128), e);
            throw new IllegalStateException("生成默认问题失败，请稍后重试", e);
        }
    }

    private String readChatContent(JsonNode contentNode) {
        if (contentNode.isTextual()) return contentNode.asText();
        if (contentNode.isArray()) {
            StringBuilder content = new StringBuilder();
            for (JsonNode part : contentNode) {
                JsonNode text = part.isTextual() ? part : part.path("text");
                if (text.isTextual()) content.append(text.asText()).append('\n');
            }
            return content.toString();
        }
        return contentNode.path("text").asText("");
    }

    private JsonNode firstArray(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isArray()) return value;
        }
        return objectMapper.createArrayNode();
    }

    private String firstSuggestedText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isTextual()) return value.asText();
        }
        return "";
    }

    private void extractCompleteJsonStrings(String json, List<String> result) {
        Matcher matcher = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"").matcher(json);
        while (matcher.find() && result.size() < 3) {
            try {
                JsonNode value = objectMapper.readTree(matcher.group());
                if (value.isTextual()) addSuggestedQuestion(result, value.asText());
            } catch (Exception ignored) {
                // Ignore incomplete or malformed JSON string fragments.
            }
        }
    }

    private void addSuggestedQuestion(List<String> questions, String candidate) {
        String question = candidate.trim();
        if (!question.isEmpty() && question.length() <= 200 && !questions.contains(question)) {
            questions.add(question);
        }
    }

    public RagDtos.QueryResponse queryForLoggedInUser(
            RagDtos.QueryRequest request, List<RagDtos.ChatHistoryMessage> history) {
        return query(request, true, history, null, null);
    }

    public RagDtos.QueryResponse queryForLoggedInUser(
            RagDtos.QueryRequest request, Consumer<String> tokenConsumer) {
        return query(request, true, List.of(), tokenConsumer, null);
    }

    public RagDtos.QueryResponse queryForLoggedInUser(
            RagDtos.QueryRequest request,
            List<RagDtos.ChatHistoryMessage> history,
            Consumer<String> tokenConsumer) {
        return query(request, true, history, tokenConsumer, null);
    }

    public RagDtos.QueryResponse queryForLoggedInUser(
            RagDtos.QueryRequest request,
            List<RagDtos.ChatHistoryMessage> history,
            Consumer<String> tokenConsumer,
            Consumer<String> reasoningConsumer) {
        return query(request, true, history, tokenConsumer, reasoningConsumer);
    }

    private RagDtos.QueryResponse query(
            RagDtos.QueryRequest request,
            boolean loggedIn,
            List<RagDtos.ChatHistoryMessage> history,
            Consumer<String> tokenConsumer,
            Consumer<String> reasoningConsumer) {
        if (request == null || request.getQuestion() == null
                || request.getQuestion().isBlank()) {
            throw new IllegalArgumentException("question不能为空");
        }
        RagProperties.Rag rag = properties.getRag();
        if (request.getChatModelId() != null) {
            AiRagChatModel chatModel = chatModelMapper.selectById(request.getChatModelId());
            if (chatModel == null) throw new BizException("聊天模型不存在");
            RagProperties.Rag selectedRag = rag.copy();
            selectedRag.setChatProvider(chatModel.getChatProvider());
            selectedRag.setChatBaseUrl(chatModel.getChatBaseUrl());
            selectedRag.setApiKey(chatModel.getApiKey());
            selectedRag.setChatModel(chatModel.getChatModel());
            selectedRag.setContextLength(chatModel.getContextLength() == null ? 8192 : chatModel.getContextLength());
            rag = selectedRag;
        }
        List<AiKnowledgeBase> knowledgeBases = new ArrayList<>();
        if (request.getKnowledgeBaseIds() != null) {
            for (Long knowledgeBaseId : request.getKnowledgeBaseIds()) {
                AiKnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(knowledgeBaseId);
                if (knowledgeBase != null) knowledgeBases.add(knowledgeBase);
            }
        } else if (request.getKnowledgeBaseId() != null) {
            AiKnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(request.getKnowledgeBaseId());
            if (knowledgeBase != null) knowledgeBases.add(knowledgeBase);
        }
        int topK = request.getTopK() == null
                ? rag.getTopK()
                : Math.max(1, Math.min(request.getTopK(), 20));

        ObjectNode filter = null;
        if (!loggedIn) {
            List<Long> publicSpiderIds = spiderMapper.selectList(
                            new LambdaQueryWrapper<Spider>().eq(Spider::getIsPublic, 1))
                    .stream().map(Spider::getId).toList();
            if (publicSpiderIds.isEmpty()) {
                return emptyQueryResponse(rag, tokenConsumer, knowledgeBases);
            }
            if (request.getSpiderId() != null
                    && !publicSpiderIds.contains(request.getSpiderId())) {
                return emptyQueryResponse(rag, tokenConsumer, knowledgeBases);
            }
            ArrayNode publicValues = objectMapper.createArrayNode();
            publicSpiderIds.forEach(publicValues::add);
            filter = objectMapper.createObjectNode();
            filter.set("terms", objectMapper.createObjectNode().set("spiderId", publicValues));
        }
        if (request.getSpiderId() != null) {
            ObjectNode spiderFilter = objectMapper.createObjectNode();
            spiderFilter.putObject("term").put("spiderId", request.getSpiderId());
            filter = combineFilters(filter, spiderFilter);
        } else if (request.getSpiderGroup() != null && !request.getSpiderGroup().isBlank()) {
            List<Long> spiderIds = spiderMapper.selectList(
                            new LambdaQueryWrapper<Spider>()
                                    .eq(Spider::getGroup, request.getSpiderGroup()))
                    .stream().map(Spider::getId).toList();
            if (spiderIds.isEmpty()) {
                return emptyQueryResponse(rag, tokenConsumer, knowledgeBases);
            }
            ArrayNode values = objectMapper.createArrayNode();
            spiderIds.forEach(values::add);
            ObjectNode groupFilter = objectMapper.createObjectNode();
            groupFilter.set("terms", objectMapper.createObjectNode().set("spiderId", values));
            filter = combineFilters(filter, groupFilter);
        }

        List<RagDtos.SourceReference> sources = new ArrayList<>();
        List<AiKnowledgeBase> searchTargets = knowledgeBases.isEmpty()
                ? java.util.Collections.singletonList(null)
                : knowledgeBases;
        for (AiKnowledgeBase knowledgeBase : searchTargets) {
            AiRagVectorModel knowledgeVectorModel = knowledgeBase == null
                    ? null : vectorModelMapper.selectById(knowledgeBase.getVectorModelId());
            if (knowledgeBase != null && knowledgeVectorModel == null) {
                throw new BizException("知识库向量模型不存在");
            }
            String vectorIndex = knowledgeBase == null
                    ? properties.resolveVectorIndex()
                    : resolveKnowledgeVectorIndex(knowledgeVectorModel);
            AiRagRerankModel rerankModel = knowledgeBase == null || knowledgeBase.getRerankModelId() == null
                    ? null : rerankModelMapper.selectById(knowledgeBase.getRerankModelId());
            if (knowledgeBase != null && knowledgeBase.getRerankModelId() != null && rerankModel == null) {
                throw new BizException("知识库关联的重排模型不存在");
            }
            int candidateCount = rerankModel == null || knowledgeVectorModel == null
                    || knowledgeVectorModel.getTopK() == null
                    ? topK
                    : Math.max(1, Math.min(knowledgeVectorModel.getTopK(), 20));
            int dimensions = knowledgeVectorModel != null && knowledgeVectorModel.getDimensions() != null
                    ? knowledgeVectorModel.getDimensions()
                    : properties.resolveDimensions();
            String vectorField = properties.resolveVectorField();
            List<Double> queryVector = knowledgeVectorModel == null
                    ? createEmbedding(request.getQuestion())
                    : createEmbedding(request.getQuestion(), knowledgeVectorModel, dimensions);
            ObjectNode script = objectMapper.createObjectNode();
            script.put("source",
                    "cosineSimilarity(params.query_vector, '" + vectorField + "') + 1.0");
            script.set("params", objectMapper.createObjectNode()
                    .set("query_vector", objectMapper.valueToTree(queryVector)));

            ObjectNode scriptScore = objectMapper.createObjectNode();
            ObjectNode body = objectMapper.createObjectNode();
            body.put("size", candidateCount);
            body.set("_source", objectMapper.valueToTree(List.of(
                    "doc_id", "chunk_id", "title", "heading", "content", "parent_text", "url", "spiderName")));
            ObjectNode searchFilter = filter;
            if (knowledgeBase != null) {
                ObjectNode knowledgeBaseFilter = objectMapper.createObjectNode();
                knowledgeBaseFilter.putObject("term")
                        .put("knowledgeBaseId", knowledgeBase.getId());
                searchFilter = combineFilters(searchFilter, knowledgeBaseFilter);
            }
            if (searchFilter != null) {
                ObjectNode boolQuery = objectMapper.createObjectNode();
                boolQuery.set("filter", objectMapper.createArrayNode().add(searchFilter));
                ObjectNode filteredQuery = objectMapper.createObjectNode();
                filteredQuery.set("bool", boolQuery);
                scriptScore.set("query", filteredQuery);
            } else {
                scriptScore.set("query", objectMapper.createObjectNode()
                        .set("match_all", objectMapper.createObjectNode()));
            }
            scriptScore.set("script", script);
            ObjectNode query = objectMapper.createObjectNode();
            query.set("script_score", scriptScore);
            body.set("query", query);

            try {
                HttpResponse<String> search = elasticsearchRequest("POST",
                        "/" + vectorIndex + "/_search", toJson(body));
                if (search.statusCode() >= 300) {
                    throw new IllegalStateException("RAG 向量检索失败: " + search.statusCode()
                            + " " + search.body());
                }
                JsonNode root = objectMapper.readTree(search.body());
                List<RagDtos.SourceReference> candidates = new ArrayList<>();
                for (JsonNode hit : root.path("hits").path("hits")) {
                    JsonNode source = hit.path("_source");
                    RagDtos.SourceReference ref = new RagDtos.SourceReference();
                    ref.setId(source.path("doc_id").asText());
                    ref.setChunkId(source.path("chunk_id").asText(""));
                    ref.setKnowledgeBaseId(knowledgeBase == null ? null : knowledgeBase.getId());
                    ref.setTitle(source.path("title").asText(""));
                    ref.setHeading(source.path("heading").asText(""));
                    ref.setUrl(source.path("url").asText(""));
                    ref.setSpiderName(source.path("spiderName").asText(""));
                    ref.setSourceType(knowledgeBase == null ? "" : knowledgeBase.getSourceType());
                    ref.setScore(hit.path("_score").asDouble(0));
                    String text = source.path("content").asText("");
                    ref.setSnippet(text.length() <= 500 ? text : text.substring(0, 500));
                    // small-to-big：命中子块后用父块（章节全文）补充上下文
                    String parentText = source.path("parent_text").asText("");
                    if (StringUtils.hasText(parentText) && parentText.length() > text.length()) {
                        ref.setSnippet(parentText.length() <= 2000
                                ? parentText : parentText.substring(0, 2000));
                    }
                    boolean duplicate = sources.stream()
                            .anyMatch(existing -> existing.getId().equals(ref.getId())
                                    && existing.getSnippet().equals(ref.getSnippet()))
                            || candidates.stream().anyMatch(existing -> existing.getId().equals(ref.getId())
                                    && existing.getSnippet().equals(ref.getSnippet()));
                    if (!duplicate) candidates.add(ref);
                }
                if (rerankModel != null && !candidates.isEmpty()) {
                    List<String> documents = candidates.stream()
                            .map(candidate -> candidate.getTitle() + "\n" + candidate.getSnippet())
                            .toList();
                    List<RerankService.Result> ranked = rerankService.rerank(
                            rerankModel, request.getQuestion(), documents);
                    for (RerankService.Result result : ranked) {
                        RagDtos.SourceReference candidate = candidates.get(result.index());
                        candidate.setScore(result.score());
                        sources.add(candidate);
                    }
                } else {
                    sources.addAll(candidates);
                }
            } catch (Exception e) {
                if (e instanceof IllegalStateException state) throw state;
                throw new IllegalStateException("RAG 向量检索失败: " + e.getMessage(), e);
            }
        }

        sources.sort(Comparator.comparingDouble(RagDtos.SourceReference::getScore).reversed());
        // 检索后近似去重：overlap 会产生高度相似的相邻 chunk，
        // 用字符 bigram Jaccard 相似度过滤，避免近似 chunk 挤占 topK 名额
        List<RagDtos.SourceReference> deduplicated = new ArrayList<>();
        for (RagDtos.SourceReference ref : sources) {
            boolean nearDuplicate = deduplicated.stream()
                    .anyMatch(existing -> existing.getId().equals(ref.getId())
                            || jaccardSimilarity(existing.getSnippet(), ref.getSnippet()) > 0.9);
            if (!nearDuplicate) deduplicated.add(ref);
        }
        if (deduplicated.size() > topK) deduplicated = new ArrayList<>(deduplicated.subList(0, topK));
        sources = deduplicated;
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            RagDtos.SourceReference ref = sources.get(i);
            String text = ref.getSnippet();
            context.append("参考资料 [").append(i + 1).append("]\n")
                    .append("标题：").append(ref.getTitle()).append("\n");
            if (StringUtils.hasText(ref.getHeading())) {
                context.append("章节：").append(ref.getHeading()).append("\n");
            }
            context.append("来源：").append(ref.getUrl()).append("\n")
                    .append("内容：").append(text).append("\n\n");
        }

        if (sources.isEmpty()) {
            return emptyQueryResponse(rag, tokenConsumer, knowledgeBases);
        }

        StreamingChatResult streamingResult = tokenConsumer == null ? null
                : createStreamingChatAnswer(
                        request.getQuestion(), context.toString(), history, tokenConsumer, reasoningConsumer, rag);
        String answer = streamingResult == null
                ? createChatAnswer(request.getQuestion(), context.toString(), history, rag)
                : streamingResult.answer();
        RagDtos.QueryResponse response = new RagDtos.QueryResponse();
        response.setAnswer(answer);
        if (streamingResult != null) {
            response.setWaitDurationMs(streamingResult.waitDurationMs());
            response.setThinkingDurationMs(streamingResult.thinkingDurationMs());
            response.setGenerationDurationMs(streamingResult.generationDurationMs());
        }
        response.setSources(sources);
        response.setModel(rag.getChatModel());
        response.setVectorIndex(resolveKnowledgeVectorIndexes(knowledgeBases));
        response.setVectorField(properties.resolveVectorField());
        return response;
    }

    private List<JsonNode> fetchContentDocuments() throws IllegalStateException {
        return fetchContentDocuments(properties.resolveSourceIndex());
    }

    private List<JsonNode> fetchContentDocuments(String sourceIndex) throws IllegalStateException {
        return fetchContentDocuments(sourceIndex, null);
    }

    private List<JsonNode> fetchContentDocuments(String sourceIndex, Long ownerKnowledgeBaseId)
            throws IllegalStateException {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("size", 1000);
            body.set("sort", objectMapper.createArrayNode().add("_doc"));
            List<String> excludedIds = getExcludedDocumentIds(sourceIndex);
            if (excludedIds.isEmpty() && ownerKnowledgeBaseId == null) {
                body.set("query", matchAllQuery());
            } else {
                ObjectNode activeQuery = objectMapper.createObjectNode();
                activeQuery.putArray("must").add(matchAllQuery());
                if (!excludedIds.isEmpty()) {
                    activeQuery.putArray("must_not").add(excludedDocumentIdsQuery(excludedIds));
                }
                if (ownerKnowledgeBaseId != null) {
                    activeQuery.putArray("filter").add(knowledgeBaseOwnerQuery(ownerKnowledgeBaseId));
                }
                body.set("query", objectMapper.createObjectNode().set("bool", activeQuery));
            }
            HttpResponse<String> response = elasticsearchRequest("POST",
                    "/" + sourceIndex + "/_search?scroll=1m", toJson(body));
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("读取 ES 源文档失败: " + response.statusCode()
                        + " " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            List<JsonNode> documents = new ArrayList<>();
            root.path("hits").path("hits").forEach(documents::add);
            String scrollId = root.path("_scroll_id").asText("");
            while (!scrollId.isBlank()) {
                ObjectNode scrollBody = objectMapper.createObjectNode();
                scrollBody.put("scroll", "1m");
                scrollBody.put("scroll_id", scrollId);
                HttpResponse<String> next = elasticsearchRequest("POST",
                        "/_search/scroll", toJson(scrollBody));
                if (next.statusCode() >= 300) break;
                root = objectMapper.readTree(next.body());
                List<JsonNode> nextHits = new ArrayList<>();
                root.path("hits").path("hits").forEach(nextHits::add);
                if (nextHits.isEmpty()) break;
                documents.addAll(nextHits);
                scrollId = root.path("_scroll_id").asText("");
            }
            if (!scrollId.isBlank()) {
                ObjectNode clearBody = objectMapper.createObjectNode();
                clearBody.putArray("scroll_id").add(scrollId);
                elasticsearchRequest("DELETE", "/_search/scroll", toJson(clearBody));
            }
            return documents;
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("读取 ES 源文档失败: " + e.getMessage(), e);
        }
    }

    private List<List<Double>> createEmbeddings(
            List<String> texts, AiRagVectorModel model, int dimensions) {
        return createEmbeddings(texts, model.getEmbeddingProvider(), model.getEmbeddingModel(),
                model.getEmbeddingBaseUrl(), model.getApiKey(), dimensions,
                text -> createEmbedding(text, model, dimensions));
    }

    private List<List<Double>> createEmbeddings(List<String> texts) {
        RagProperties.Rag rag = properties.getRag();
        return createEmbeddings(texts, rag.getEmbeddingProvider(), rag.getEmbeddingModel(),
                rag.getEmbeddingBaseUrl(), rag.getEmbeddingApiKey(), properties.resolveDimensions(),
                this::createEmbedding);
    }

    private List<List<Double>> createEmbeddings(
            List<String> texts, String provider, String model, String baseUrl,
            String apiKey, int dimensions, Function<String, List<Double>> singleEmbedding) {
        if (texts.isEmpty()) return List.of();
        boolean ollama = "ollama".equalsIgnoreCase(provider);
        String path = ollama ? "/api/embed" : "/embeddings";
        try {
            URI baseUri = URI.create(baseUrl.trim().replaceAll("/$", ""));
            String normalizedBaseUrl = ollama
                    ? baseUri.toString().replaceAll("/v1$", "")
                    : baseUri.toString();
            ObjectNode body = objectMapper.createObjectNode().put("model", model);
            body.set("input", objectMapper.valueToTree(texts));
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(normalizedBaseUrl + path))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json");
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            HttpRequest request = builder.POST(HttpRequest.BodyPublishers
                            .ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = EMBEDDING_HTTP_CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (ollama && (response.statusCode() == 404 || response.statusCode() == 405)) {
                log.info("Embedding 服务不支持批量接口，回退到逐条请求: model={}", model);
                return texts.stream().map(singleEmbedding).toList();
            }
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("调用 Embedding 模型失败: " + response.statusCode()
                        + " " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode rows = root.path("data").isArray() ? root.path("data")
                    : root.path("embeddings").isArray() ? root.path("embeddings") : null;
            List<List<Double>> results = new ArrayList<>(texts.size());
            for (int i = 0; i < texts.size(); i++) results.add(null);
            if (rows != null) {
                int nextIndex = 0;
                for (JsonNode row : rows) {
                    JsonNode vector = row.isArray() ? row : row.path("embedding");
                    int index = row.path("index").isNumber()
                            ? row.path("index").asInt() : nextIndex;
                    nextIndex++;
                    if (index >= 0 && index < results.size()) {
                        results.set(index, parseEmbeddingVector(vector, dimensions));
                    }
                }
            } else if (texts.size() == 1 && root.path("embedding").isArray()) {
                results.set(0, parseEmbeddingVector(root.path("embedding"), dimensions));
            }
            if (results.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalStateException("Embedding 模型返回的向量数量与请求数量不一致");
            }
            return results;
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("调用 Embedding 模型失败: " + e.getMessage(), e);
        }
    }

    private List<Double> parseEmbeddingVector(JsonNode vector, int dimensions) {
        List<Double> result = new ArrayList<>();
        if (vector.isArray()) {
            for (JsonNode value : vector) result.add(value.asDouble());
        }
        if (result.isEmpty()) throw new IllegalStateException("Embedding 模型未返回向量");
        if (result.size() != dimensions) {
            throw new IllegalStateException("Embedding 向量维度不匹配: 实际 "
                    + result.size() + "，配置 " + dimensions);
        }
        return result;
    }

    private List<Double> createEmbedding(String text, AiRagVectorModel model, int dimensions) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model.getEmbeddingModel());
            body.put("ollama".equalsIgnoreCase(model.getEmbeddingProvider()) ? "prompt" : "input", text);
            String path = "ollama".equalsIgnoreCase(model.getEmbeddingProvider())
                    ? "/api/embeddings"
                    : "/embeddings";
            URI baseUri = URI.create(model.getEmbeddingBaseUrl().trim().replaceAll("/$", ""));
            String baseUrl = path.equals("/api/embeddings")
                    ? baseUri.toString().replaceAll("/v1$", "")
                    : baseUri.toString();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json");
            if (model.getApiKey() != null && !model.getApiKey().isBlank()) {
                builder.header("Authorization", "Bearer " + model.getApiKey());
            }
            HttpRequest request = builder.POST(HttpRequest.BodyPublishers
                            .ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = EMBEDDING_HTTP_CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("调用 Embedding 模型失败: " + response.statusCode()
                        + " " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode vector = root.path("embedding").isArray()
                    ? root.path("embedding")
                    : root.path("embeddings").isArray() && root.path("embeddings").size() > 0
                            && root.path("embeddings").get(0).isArray()
                    ? root.path("embeddings").get(0)
                    : root.path("data").isArray() && root.path("data").size() > 0
                            ? root.path("data").get(0).path("embedding")
                            : objectMapper.createArrayNode();
            List<Double> result = new ArrayList<>();
            if (vector.isArray()) {
                for (JsonNode value : vector) result.add(value.asDouble());
            }
            if (result.isEmpty()) throw new IllegalStateException("Embedding 模型未返回向量");
            if (result.size() != dimensions) {
                throw new IllegalStateException("Embedding 向量维度不匹配: 实际 "
                        + result.size() + "，配置 " + dimensions);
            }
            return result;
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("调用 Embedding 模型失败: " + e.getMessage(), e);
        }
    }

    private List<Double> createEmbedding(String text) {
        RagProperties.Rag rag = properties.getRag();
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", rag.getEmbeddingModel());
            body.put("ollama".equalsIgnoreCase(rag.getEmbeddingProvider()) ? "prompt" : "input", text);
            String path = "ollama".equalsIgnoreCase(rag.getEmbeddingProvider())
                    ? "/api/embeddings"
                    : "/embeddings";
            URI uri = URI.create(rag.getEmbeddingBaseUrl().trim().replaceAll("/$", ""));
            String baseUrl = path.equals("/api/embeddings")
                    ? uri.toString().replaceAll("/v1$", "")
                    : uri.toString();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json");
            if (rag.getEmbeddingApiKey() != null && !rag.getEmbeddingApiKey().isBlank()) {
                builder.header("Authorization", "Bearer " + rag.getEmbeddingApiKey());
            }
            HttpRequest request = builder.POST(HttpRequest.BodyPublishers
                            .ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = EMBEDDING_HTTP_CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("调用 Embedding 模型失败: " + response.statusCode()
                        + " " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode vector;
            if (root.path("embedding").isArray()) {
                vector = root.path("embedding");
            } else if (root.path("embeddings").isArray()
                    && root.path("embeddings").size() > 0
                    && root.path("embeddings").get(0).isArray()) {
                vector = root.path("embeddings").get(0);
            } else if (root.path("data").isArray()
                    && root.path("data").size() > 0) {
                vector = root.path("data").get(0).path("embedding");
            } else {
                vector = objectMapper.createArrayNode();
            }
            List<Double> result = new ArrayList<>();
            if (vector.isArray()) {
                for (JsonNode value : vector) result.add(value.asDouble());
            }
            if (result.isEmpty()) {
                throw new IllegalStateException("Embedding 模型未返回向量");
            }
            if (result.size() != properties.resolveDimensions()) {
                throw new IllegalStateException("Embedding 向量维度不匹配: 实际 "
                        + result.size() + "，配置 " + properties.resolveDimensions());
            }
            return result;
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("调用 Embedding 模型失败: " + e.getMessage(), e);
        }
    }

    private String createChatAnswer(
            String question, String context, List<RagDtos.ChatHistoryMessage> history,
            RagProperties.Rag rag) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", rag.getChatModel());
            ArrayNode messages = body.putArray("messages");
            appendChatMessages(messages, question, context, history);
            if (!"ollama".equalsIgnoreCase(rag.getChatProvider())) {
                body.put("temperature", 0.2);
            } else {
                body.put("stream", false);
                body.putObject("options").put("num_ctx", rag.getContextLength());
            }
            String path = "ollama".equalsIgnoreCase(rag.getChatProvider())
                    ? "/api/chat"
                    : "/chat/completions";
            HttpResponse<String> response = openAiRequest(path, body, rag);
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("调用聊天模型失败: " + response.statusCode()
                        + " " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            if ("ollama".equalsIgnoreCase(rag.getChatProvider())) {
                return root.path("message").path("content").asText("");
            }
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new IllegalStateException("聊天模型未返回 choices");
            }
            return choices.get(0).path("message").path("content").asText("");
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("调用聊天模型失败: " + e.getMessage(), e);
        }
    }

    private StreamingChatResult createStreamingChatAnswer(
            String question,
            String context,
            List<RagDtos.ChatHistoryMessage> history,
            Consumer<String> tokenConsumer,
            Consumer<String> reasoningConsumer,
            RagProperties.Rag rag) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", rag.getChatModel());
            ArrayNode messages = body.putArray("messages");
            appendChatMessages(messages, question, context, history);
            String provider = rag.getChatProvider();
            String path = "ollama".equalsIgnoreCase(provider)
                    ? "/api/chat"
                    : "/chat/completions";
            body.put("stream", true);
            if (!"ollama".equalsIgnoreCase(provider)) {
                body.put("temperature", 0.2);
            } else {
                body.put("think", true);
                body.putObject("options").put("num_ctx", rag.getContextLength());
            }
            long startNanos = System.nanoTime();
            AtomicLong firstOutputNanos = new AtomicLong();
            AtomicLong firstReasoningNanos = new AtomicLong();
            AtomicLong firstTokenNanos = new AtomicLong();
            Consumer<String> timedTokenConsumer = token -> {
                long now = System.nanoTime();
                firstOutputNanos.compareAndSet(0, now);
                firstTokenNanos.compareAndSet(0, now);
                tokenConsumer.accept(token);
            };
            Consumer<String> timedReasoningConsumer = reasoning -> {
                firstOutputNanos.compareAndSet(0, System.nanoTime());
                firstReasoningNanos.compareAndSet(0, System.nanoTime());
                if (reasoningConsumer != null) reasoningConsumer.accept(reasoning);
            };
            streamChatRequest(path, body, timedTokenConsumer, timedReasoningConsumer, rag);
            long completedNanos = System.nanoTime();
            long firstToken = firstTokenNanos.get();
            long firstOutput = firstOutputNanos.get();
            long firstReasoning = firstReasoningNanos.get();
            long waitEnd = firstOutput == 0 ? completedNanos : firstOutput;
            long waitDurationMs = Duration.ofNanos(waitEnd - startNanos).toMillis();
            Long thinkingDurationMs = firstReasoning == 0 ? null
                    : Duration.ofNanos(Math.max(0,
                            (firstToken == 0 ? completedNanos : firstToken) - firstReasoning)).toMillis();
            long generationDurationMs = firstToken == 0 ? 0
                    : Duration.ofNanos(completedNanos - firstToken).toMillis();
            return new StreamingChatResult("", waitDurationMs, thinkingDurationMs, generationDurationMs);
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("调用聊天模型失败: " + e.getMessage(), e);
        }
    }

    private void appendChatMessages(
            ArrayNode messages,
            String question,
            String context,
            List<RagDtos.ChatHistoryMessage> history) {
        boolean hasContext = context != null && !context.isBlank();
        messages.addObject()
                .put("role", "system")
                .put("content", hasContext
                        ? "你是一个只依据提供资料回答问题的中文助手。"
                                + "如果资料不足，请明确说明不能确定，不要编造。"
                                + "回答中的每个事实性段落都要在相关内容后标注对应资料编号，格式为 [1]；"
                                + "若有多个来源支持，可连续标注 [1][2]。编号必须来自提供的参考资料，"
                                + "不要把所有引用统一放到答案末尾。"
                        : "你是一个友好的中文助手，请结合当前对话历史直接、准确地回答用户问题。");
        if (history != null) {
            for (RagDtos.ChatHistoryMessage item : history) {
                if (item == null || item.getContent() == null || item.getContent().isBlank()) {
                    continue;
                }
                if (!"user".equals(item.getRole()) && !"assistant".equals(item.getRole())) {
                    continue;
                }
                messages.addObject()
                        .put("role", item.getRole())
                        .put("content", item.getContent());
            }
        }
        String currentMessage = hasContext
                ? "资料：\n" + context + "\n\n问题：" + question
                : question;
        messages.addObject().put("role", "user").put("content", currentMessage);
    }

            private void streamChatRequest(
                    String path,
                    ObjectNode body,
                    Consumer<String> tokenConsumer,
                    Consumer<String> reasoningConsumer,
                    RagProperties.Rag rag) throws Exception {
                String baseUrl = path.equals("/api/chat")
                        ? rag.getChatBaseUrl().replaceAll("/$", "").replaceAll("/v1$", "")
                        : rag.getChatBaseUrl().replaceAll("/$", "");
                URI uri = URI.create(baseUrl + path);
                String requestBody = objectMapper.writeValueAsString(body);
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(Duration.ofSeconds(120))
                        .header("Content-Type", "application/json");
                String apiKey = rag.getApiKey();
                boolean hasApiKey = apiKey != null && !apiKey.isBlank();
                if (hasApiKey) {
                    builder.header("Authorization", "Bearer " + apiKey);
                }
                HttpRequest request = builder.POST(HttpRequest.BodyPublishers
                        .ofString(requestBody, StandardCharsets.UTF_8)).build();
                long startNanos = System.nanoTime();
                String logUri = safeLogUri(uri);
                log.info("发送流式聊天请求: method=POST, url={}, provider={}, model={}, bodyBytes={}, apiKeyConfigured={}",
                        logUri, rag.getChatProvider(), safeLogValue(rag.getChatModel(), 128),
                        requestBody.getBytes(StandardCharsets.UTF_8).length, hasApiKey);

                try {
                    HttpResponse<java.io.InputStream> response = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(10)).build()
                            .send(request, HttpResponse.BodyHandlers.ofInputStream());
                    if (response.statusCode() >= 300) {
                        try (java.io.InputStream input = response.body()) {
                            String errorBody = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                            log.warn("流式聊天请求返回错误: url={}, status={}, responseBytes={}, elapsedMs={}",
                                    logUri, response.statusCode(),
                                    errorBody.getBytes(StandardCharsets.UTF_8).length,
                                    Duration.ofNanos(System.nanoTime() - startNanos).toMillis());
                            throw new IllegalStateException("调用聊天模型失败: " + response.statusCode()
                                    + " " + errorBody);
                        }
                    }
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                        if (path.equals("/api/chat")) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (!line.isBlank()) emitOllamaToken(line, tokenConsumer, reasoningConsumer);
                            }
                        } else {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.startsWith("data:")) {
                                    emitOpenAiToken(line.substring(5).trim(), tokenConsumer, reasoningConsumer);
                                }
                            }
                        }
                    }
                    log.info("流式聊天请求完成: url={}, provider={}, model={}, elapsedMs={}",
                            logUri, rag.getChatProvider(), safeLogValue(rag.getChatModel(), 128),
                            Duration.ofNanos(System.nanoTime() - startNanos).toMillis());
                } catch (Exception e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    log.warn("流式聊天请求失败: url={}, provider={}, model={}, exception={}, elapsedMs={}",
                            logUri, rag.getChatProvider(), safeLogValue(rag.getChatModel(), 128),
                            e.getClass().getSimpleName(),
                            Duration.ofNanos(System.nanoTime() - startNanos).toMillis());
                    throw e;
                }
            }

            private void emitOllamaToken(
                    String line, Consumer<String> tokenConsumer, Consumer<String> reasoningConsumer) throws Exception {
                JsonNode root = objectMapper.readTree(line);
                JsonNode message = root.path("message");
                String token = message.path("content").asText("");
                if (!token.isEmpty()) tokenConsumer.accept(token);
                String reasoning = message.path("thinking").asText("");
                if (!reasoning.isEmpty() && reasoningConsumer != null) reasoningConsumer.accept(reasoning);
                if (root.path("error").isTextual()) {
                    throw new IllegalStateException("聊天模型返回错误: " + root.path("error").asText());
                }
            }

            private void emitOpenAiToken(
                    String data, Consumer<String> tokenConsumer, Consumer<String> reasoningConsumer) throws Exception {
                if (data.isEmpty() || "[DONE]".equals(data)) return;
                JsonNode root = objectMapper.readTree(data);
                JsonNode choices = root.path("choices");
                if (!choices.isArray() || choices.isEmpty()) return;
                JsonNode delta = choices.get(0).path("delta");
                String token = delta.path("content").asText("");
                if (!token.isEmpty()) tokenConsumer.accept(token);
                String reasoning = firstText(delta, "reasoning_content", "reasoning", "thinking");
                if (!reasoning.isEmpty() && reasoningConsumer != null) reasoningConsumer.accept(reasoning);
            }

            private String firstText(JsonNode node, String... fields) {
                for (String field : fields) {
                    String value = node.path(field).asText("");
                    if (!value.isEmpty()) return value;
                }
                return "";
            }

    private HttpResponse<String> openAiRequest(String path, ObjectNode body) throws Exception {
        return openAiRequest(path, body, properties.getRag());
    }

    private HttpResponse<String> openAiRequest(
            String path, ObjectNode body, RagProperties.Rag rag) throws Exception {
        String baseUrl;
        if (path.equals("/api/embeddings")) {
            baseUrl = rag.getEmbeddingBaseUrl().replaceAll("/$", "").replaceAll("/v1$", "");
        } else if (path.equals("/api/chat")) {
            baseUrl = rag.getChatProvider().equalsIgnoreCase("ollama")
                    ? rag.getChatBaseUrl().replaceAll("/$", "").replaceAll("/v1$", "")
                    : rag.getChatBaseUrl().replaceAll("/$", "");
        } else if (path.startsWith("/chat/completions")) {
            baseUrl = rag.getChatBaseUrl().replaceAll("/$", "");
        } else if (path.equals("/embeddings")) {
            baseUrl = rag.getEmbeddingBaseUrl().replaceAll("/$", "");
        } else {
            throw new IllegalArgumentException("不支持的模型 API 路径: " + path);
        }
        URI requestUri = URI.create(baseUrl + path);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(requestUri)
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json");
        boolean embeddingRequest = path.equals("/api/embeddings") || path.equals("/embeddings");
        String apiKey = embeddingRequest ? rag.getEmbeddingApiKey() : rag.getApiKey();
        boolean hasApiKey = apiKey != null && !apiKey.isBlank();
        if (hasApiKey) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        String requestBody = objectMapper.writeValueAsString(body);
        HttpRequest request = builder.POST(HttpRequest.BodyPublishers
                        .ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        boolean chatRequest = path.equals("/api/chat") || path.startsWith("/chat/completions");
        long startNanos = System.nanoTime();
        String logUri = chatRequest ? safeLogUri(requestUri) : null;
        String chatModel = chatRequest ? safeLogValue(rag.getChatModel(), 128) : null;
        if (chatRequest) {
            log.info("发送聊天模型请求: method=POST, url={}, provider={}, model={}, contentType=application/json, "
                            + "bodyBytes={}, messages={}, apiKeyConfigured={}",
                    logUri, rag.getChatProvider(), chatModel,
                    requestBody.getBytes(StandardCharsets.UTF_8).length,
                    summarizeMessages(body.path("messages")), hasApiKey);
        }
        try {
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10)).build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (chatRequest) {
                long elapsedMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
                String contentType = response.headers().firstValue("content-type").orElse("unknown");
                if (response.statusCode() >= 300) {
                    log.warn("聊天模型请求返回错误: url={}, provider={}, model={}, status={}, "
                                    + "contentType={}, responseBytes={}, elapsedMs={}",
                            logUri, rag.getChatProvider(), chatModel, response.statusCode(),
                            contentType, response.body().getBytes(StandardCharsets.UTF_8).length, elapsedMs);
                } else {
                    log.info("聊天模型请求完成: url={}, provider={}, model={}, status={}, "
                                    + "contentType={}, responseBytes={}, elapsedMs={}",
                            logUri, rag.getChatProvider(), chatModel, response.statusCode(),
                            contentType, response.body().getBytes(StandardCharsets.UTF_8).length, elapsedMs);
                }
            }
            return response;
        } catch (Exception e) {
            if (chatRequest) {
                long elapsedMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
                log.warn("聊天模型请求未收到响应: url={}, provider={}, model={}, exception={}, "
                                + "elapsedMs={}",
                        logUri, rag.getChatProvider(), chatModel,
                        e.getClass().getSimpleName(), elapsedMs);
            }
            throw e;
        }
    }

    private static String safeLogUri(URI uri) throws URISyntaxException {
        return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(),
                uri.getPath(), null, null).toString();
    }

    private static String safeLogValue(String value, int maxLength) {
        if (value == null) {
            return "unknown";
        }
        String sanitized = value.replaceAll("[\\p{Cntrl}]", "?");
        return sanitized.length() <= maxLength
                ? sanitized : sanitized.substring(0, maxLength);
    }

    private static List<String> summarizeMessages(JsonNode messages) {
        List<String> summary = new ArrayList<>();
        if (!messages.isArray()) {
            return summary;
        }
        for (JsonNode message : messages) {
            summary.add(message.path("role").asText("unknown") + ":"
                    + message.path("content").asText("").length() + "chars");
        }
        return summary;
    }

    private HttpResponse<String> elasticsearchRequest(String method, String path, String body)
            throws Exception {
        String uri = elasticsearchUris.split(",")[0].trim();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(uri + path))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json");
        if (!elasticsearchUsername.isBlank()) {
            String auth = elasticsearchUsername + ":" + elasticsearchPassword;
            builder.header("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
        }
        HttpRequest request = (body == null
                ? builder.method(method, HttpRequest.BodyPublishers.noBody())
                : builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)))
                .build();
        return ELASTICSEARCH_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void deleteExistingVectors() {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("query", objectMapper.createObjectNode()
                .set("term", objectMapper.createObjectNode()
                        .put("embedding_model", properties.getRag().getEmbeddingModel())));
        try {
            HttpResponse<String> response = elasticsearchRequest("POST",
                    "/" + properties.resolveVectorIndex() + "/_delete_by_query?refresh=true&conflicts=proceed",
                    toJson(body));
            if (response.statusCode() >= 300 && response.statusCode() != 404) {
                throw new IllegalStateException("清理旧 RAG 向量失败: " + response.statusCode()
                        + " " + response.body());
            }
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("清理旧 RAG 向量失败: " + e.getMessage(), e);
        }
    }

    private void bulkOperations(List<ObjectNode> operations) {
        bulkOperations(operations, true);
    }

    private void bulkOperations(List<ObjectNode> operations, boolean refresh) {
        StringBuilder bulk = new StringBuilder();
        for (ObjectNode operation : operations) {
            bulk.append(toJson(operation)).append('\n');
        }
        try {
            HttpResponse<String> response = elasticsearchRequest("POST",
                    "/_bulk?refresh=" + refresh, bulk.toString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("写入 RAG 向量索引失败: " + response.statusCode()
                        + " " + response.body());
            }
            JsonNode result = objectMapper.readTree(response.body());
            if (result.path("errors").asBoolean(false)) {
                String firstError = "ES _bulk 未返回具体错误";
                JsonNode items = result.path("items");
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        if (item.path("index").path("error").isObject()) {
                            firstError = item.path("index").path("error").toString();
                            break;
                        }
                    }
                }
                throw new IllegalStateException("写入 RAG 向量索引失败: " + firstError);
            }
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("写入 RAG 向量索引失败: " + e.getMessage(), e);
        }
    }

    private void refreshIndex(String index) {
        try {
            HttpResponse<String> response = elasticsearchRequest(
                    "POST", "/" + index + "/_refresh", null);
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("刷新向量索引失败: " + response.statusCode()
                        + " " + response.body());
            }
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("刷新向量索引失败: " + e.getMessage(), e);
        }
    }

    private boolean isLoggedIn() {
        try {
            LoginUtils.getLoginUser();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private ObjectNode combineFilters(ObjectNode existing, ObjectNode additional) {
        if (existing == null) return additional;
        if (existing.path("bool").isObject()) {
            ObjectNode bool = (ObjectNode) existing.get("bool");
            ArrayNode filters = bool.path("filter").isArray()
                    ? (ArrayNode) bool.get("filter")
                    : bool.putArray("filter");
            filters.add(additional);
            return existing;
        }
        ObjectNode bool = objectMapper.createObjectNode();
        ArrayNode filters = bool.putArray("filter");
        filters.add(existing);
        filters.add(additional);
        return objectMapper.createObjectNode().set("bool", bool);
    }

    private RagDtos.QueryResponse emptyQueryResponse(
            RagProperties.Rag rag,
            Consumer<String> tokenConsumer,
            List<AiKnowledgeBase> knowledgeBases) {
        RagDtos.QueryResponse response = new RagDtos.QueryResponse();
        response.setAnswer(NO_RETRIEVED_RECORDS_ANSWER);
        if (tokenConsumer != null) tokenConsumer.accept(NO_RETRIEVED_RECORDS_ANSWER);
        response.setSources(List.of());
        response.setModel(rag.getChatModel());
        response.setVectorIndex(resolveKnowledgeVectorIndexes(knowledgeBases));
        response.setVectorField(properties.resolveVectorField());
        return response;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 RAG 请求失败: " + e.getMessage(), e);
        }
    }

    private static final String SENTENCE_BOUNDARY_CHARS = "。！？!?；;，,.:：、\n\t";

    private static List<String> splitText(String text, int chunkSize, int overlap) {
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
     * 返回切分位置（边界字符之后）；找不到合适的边界时返回 -1。
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

    private static AiRagVectorModel vectorModelFromProperties(RagProperties.Rag rag) {
        AiRagVectorModel model = new AiRagVectorModel();
        model.setEmbeddingProvider(rag.getEmbeddingProvider());
        model.setEmbeddingBaseUrl(rag.getEmbeddingBaseUrl());
        model.setApiKey(rag.getEmbeddingApiKey());
        model.setEmbeddingModel(rag.getEmbeddingModel());
        model.setDimensions(rag.getDimensions());
        return model;
    }

    private ChunkingResult splitText(
            String text, Set<String> documentTypes,
            List<AiRagChunkStrategy> strategies, AiRagVectorModel model) {
        if (text == null || text.isBlank()) {
            return new ChunkingResult(List.of(), null, List.of(), List.of());
        }
        AiRagChunkStrategy strategy = chunkStrategyService.selectForDocument(
                text.length(), documentTypes, strategies);
        int chunkSize = strategy.getChunkSize();
        int overlap = strategy.getChunkOverlap() == null ? 0 : strategy.getChunkOverlap();
        // 分块长度不能超过 embedding 模型的最大输入 token（按 1 token ≈ 1 字符保守估算）
        if (model != null && model.getMaxTokens() != null && model.getMaxTokens() > 0
                && chunkSize > model.getMaxTokens()) {
            log.warn("分块策略 [{}] 的分块长度 {} 超过向量模型 [{}] 最大 Token 数 {}，已自动截断",
                    strategy.getName(), chunkSize, model.getEmbeddingModel(), model.getMaxTokens());
            chunkSize = model.getMaxTokens();
            overlap = Math.min(overlap, Math.max(0, chunkSize / 4));
        }
        // 按 Markdown 标题层级切段，让每个 chunk 携带章节上下文；
        // 无标题结构的文档退化为单段（heading 为空）。
        List<HeadingSection> sections = splitHeadingSections(text);
        List<String> chunks = new ArrayList<>();
        List<String> headings = new ArrayList<>();
        List<String> parentTexts = new ArrayList<>();
        for (HeadingSection section : sections) {
            List<String> sectionChunks = switch (strategy.getStrategyType()) {
                case ChunkStrategyService.FIXED_CHARACTER -> splitText(section.text(), chunkSize, overlap);
                case ChunkStrategyService.PARAGRAPH -> splitParagraphs(section.text(), chunkSize, overlap);
                case ChunkStrategyService.RECURSIVE -> splitRecursively(section.text(), chunkSize, overlap);
                case ChunkStrategyService.SEMANTIC -> splitSemantically(section.text(), strategy, model);
                default -> throw new BizException("不支持的分块策略类型: " + strategy.getStrategyType());
            };
            // 父块 = 所属章节全文（超长时截断），检索命中子块后用它补充上下文
            String parentText = section.text().length() > PARENT_CHUNK_MAX_LENGTH
                    ? section.text().substring(0, PARENT_CHUNK_MAX_LENGTH)
                    : section.text();
            for (String chunk : sectionChunks) {
                chunks.add(chunk);
                headings.add(section.heading());
                parentTexts.add(parentText);
            }
        }
        return new ChunkingResult(chunks, strategy, headings, parentTexts);
    }

    /**
     * 按 Markdown 标题（# ~ ######）把文档切成带章节路径的段落。
     * 标题行本身并入其所属段落的开头，作为该段所有 chunk 的上下文；
     * 非 Markdown 文档（无标题行）返回单个空 heading 段落。
     */
    private static List<HeadingSection> splitHeadingSections(String text) {
        List<HeadingSection> sections = new ArrayList<>();
        String[] lines = text.split("\\R", -1);
        // 各级标题栈：level -> title
        java.util.TreeMap<Integer, String> headingStack = new java.util.TreeMap<>();
        StringBuilder current = new StringBuilder();
        String currentHeading = "";
        boolean hasHeading = false;
        for (String line : lines) {
            Matcher matcher = MARKDOWN_HEADING_PATTERN.matcher(line);
            if (matcher.matches()) {
                if (current.length() > 0) {
                    sections.add(new HeadingSection(currentHeading, current.toString().trim()));
                    current.setLength(0);
                }
                int level = matcher.group(1).length();
                headingStack.tailMap(level, true).clear();
                headingStack.put(level, matcher.group(2).trim());
                currentHeading = String.join(" / ", headingStack.values());
                hasHeading = true;
                current.append(line).append('\n');
            } else {
                current.append(line).append('\n');
            }
        }
        if (current.length() > 0) {
            sections.add(new HeadingSection(currentHeading, current.toString().trim()));
        }
        if (!hasHeading) {
            // 无标题结构：合并为单段，heading 置空
            StringBuilder merged = new StringBuilder();
            for (HeadingSection section : sections) {
                if (merged.length() > 0) merged.append('\n');
                merged.append(section.text());
            }
            return List.of(new HeadingSection("", merged.toString().trim()));
        }
        return sections;
    }

    private static Set<String> documentTypes(JsonNode source) {
        Set<String> types = new HashSet<>();
        for (String field : List.of(
                "originalName", "fileName", "filename", "url", "title")) {
            addFileExtension(types, source.path(field).asText(""));
        }
        for (String field : List.of("contentType", "content_type", "fileType", "type")) {
            String value = source.path(field).asText("").trim().toLowerCase(java.util.Locale.ROOT);
            if (value.contains(";")) value = value.substring(0, value.indexOf(';')).trim();
            if (!value.isBlank()) types.add(value);
            addFileExtension(types, value);
        }
        return types;
    }

    private static String documentText(String title, String content) {
        return title == null || title.isBlank() ? content : title + "\n" + content;
    }

    private static void addFileExtension(Set<String> types, String fileName) {
        if (!StringUtils.hasText(fileName)) return;
        String path = fileName.trim().split("[?#]", 2)[0];
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            types.add(name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT));
        }
    }

    private static Set<String> documentTypes(String fileName, String contentType) {
        Set<String> types = new HashSet<>();
        addFileExtension(types, fileName);
        if (StringUtils.hasText(contentType)) {
            String mimeType = contentType.split(";", 2)[0].trim()
                    .toLowerCase(java.util.Locale.ROOT);
            types.add(mimeType);
        }
        return types;
    }

    private static boolean isMarkdownDocument(String fileName, String contentType) {
        String name = fileName == null ? "" : fileName.trim().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".md") || name.endsWith(".markdown")) return true;
        return StringUtils.hasText(contentType)
                && contentType.toLowerCase(java.util.Locale.ROOT).contains("markdown");
    }

    private static String decodeMarkdown(java.io.InputStream input) throws java.io.IOException {
        byte[] bytes = input.readAllBytes();
        if (bytes.length >= 2) {
            if ((bytes[0] & 0xff) == 0xfe && (bytes[1] & 0xff) == 0xff) {
                byte[] bomRemoved = java.util.Arrays.copyOfRange(bytes, 2, bytes.length);
                return new String(bomRemoved, java.nio.charset.Charset.forName("UTF-16BE"))
                        .replace('\u0000', ' ')
                        .replace("\r\n", "\n");
            }
            if ((bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xfe) {
                byte[] bomRemoved = java.util.Arrays.copyOfRange(bytes, 2, bytes.length);
                return new String(bomRemoved, java.nio.charset.Charset.forName("UTF-16LE"))
                        .replace('\u0000', ' ')
                        .replace("\r\n", "\n");
            }
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xef
                && (bytes[1] & 0xff) == 0xbb
                && (bytes[2] & 0xff) == 0xbf) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
        }
        return new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String extractTextWithTika(java.io.InputStream input) throws Exception {
        BodyContentHandler handler = new BodyContentHandler(-1);
        AutoDetectParser parser = new AutoDetectParser();
        parser.parse(input, handler, new Metadata(), new ParseContext());
        return handler.toString();
    }

    private static List<String> splitParagraphs(String text, int chunkSize, int overlap) {
        // 按空行（两个及以上换行，中间可含空白）切分段落
        List<String> paragraphs = java.util.Arrays.stream(text.split("\\R\\s*\\R"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        return packSegments(paragraphs, chunkSize, overlap, "\n\n");
    }

    private static List<String> splitRecursively(String text, int chunkSize, int overlap) {
        List<String> segments = new ArrayList<>();
        splitRecursively(text, 0, chunkSize, segments);
        return packSegments(segments, chunkSize, overlap);
    }

    private static final List<String> RECURSIVE_SEPARATORS = List.of(
            "\n\n", "\n", "。", "！", "？", "……", "...", ".", "!", "?",
            "；", ";", "：", ":", "，", ",", "、", "\t", " ");

    private static void splitRecursively(
            String text, int separatorIndex, int chunkSize, List<String> segments) {
        if (text.length() <= chunkSize) {
            segments.add(text);
            return;
        }
        if (separatorIndex >= RECURSIVE_SEPARATORS.size()) {
            segments.addAll(splitText(text, chunkSize, 0));
            return;
        }
        List<String> split = splitKeepingSeparator(text, RECURSIVE_SEPARATORS.get(separatorIndex));
        if (split.size() == 1) {
            splitRecursively(text, separatorIndex + 1, chunkSize, segments);
            return;
        }
        for (String part : split) {
            splitRecursively(part, separatorIndex + 1, chunkSize, segments);
        }
    }

    private static List<String> splitKeepingSeparator(String text, String separator) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        int position;
        while ((position = text.indexOf(separator, start)) >= 0) {
            int end = position + separator.length();
            parts.add(text.substring(start, end));
            start = end;
        }
        if (start < text.length()) parts.add(text.substring(start));
        return parts;
    }

    private static List<String> packSegments(List<String> segments, int chunkSize, int overlap) {
        return packSegments(segments, chunkSize, overlap, " ");
    }

    private static List<String> packSegments(
            List<String> segments, int chunkSize, int overlap, String separator) {
        List<String> expanded = new ArrayList<>();
        for (String segment : segments) {
            String value = segment.trim();
            if (value.length() > chunkSize) {
                expanded.addAll(splitText(value, chunkSize, overlap));
            } else if (!value.isEmpty()) {
                expanded.add(value);
            }
        }
        List<String> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentLength = 0;
        for (String segment : expanded) {
            if (!current.isEmpty()
                    && currentLength + segment.length() + separator.length() > chunkSize) {
                chunks.add(String.join(separator, current).trim());
                // 从上一块末尾回带完整 segment 作为重叠，避免从句子/单词中间截断
                int maxCarry = Math.min(overlap,
                        Math.max(0, chunkSize - segment.length() - separator.length()));
                List<String> carried = new ArrayList<>();
                int carriedLength = 0;
                for (int k = current.size() - 1; k >= 0; k--) {
                    int segmentLength = current.get(k).length()
                            + (carried.isEmpty() ? 0 : separator.length());
                    if (carriedLength + segmentLength > maxCarry) break;
                    carried.add(0, current.get(k));
                    carriedLength += segmentLength;
                }
                current = carried;
                currentLength = carriedLength;
            }
            if (current.isEmpty()) {
                currentLength = segment.length();
            } else {
                currentLength += separator.length() + segment.length();
            }
            current.add(segment);
        }
        if (!current.isEmpty()) chunks.add(String.join(separator, current).trim());
        return chunks;
    }

    private List<String> splitSemantically(
            String text, AiRagChunkStrategy strategy, AiRagVectorModel model) {
        int chunkSize = strategy.getChunkSize();
        List<String> sentences = new ArrayList<>();
        for (String sentence : text.split("(?<=[。！？!?])\\s*|\\R+")) {
            String value = sentence.trim();
            if (value.length() > chunkSize) {
                sentences.addAll(splitText(value, chunkSize, 0));
            } else if (!value.isEmpty()) {
                sentences.add(value);
            }
        }
        if (sentences.isEmpty()) return List.of();
        double threshold = strategy.getSimilarityThreshold() == null
                ? 0.7 : strategy.getSimilarityThreshold().doubleValue();
        int minChunkSize = Math.max(1, chunkSize / 4);
        int overlap = strategy.getChunkOverlap() == null
                ? 0 : Math.max(0, Math.min(strategy.getChunkOverlap(), chunkSize / 2));

        // 流式分块：按批取 embedding，边算边界边产出 chunk，
        // 避免一次性持有全文向量，并支持最小块长度与边界重叠。
        List<String> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        List<List<Double>> currentEmbeddings = new ArrayList<>();
        int currentLength = 0;
        for (int start = 0; start < sentences.size(); start += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(start + EMBEDDING_BATCH_SIZE, sentences.size());
            List<List<Double>> batchEmbeddings = createEmbeddings(
                    sentences.subList(start, end), model, model.getDimensions());
            for (int j = start; j < end; j++) {
                String sentence = sentences.get(j);
                List<Double> embedding = batchEmbeddings.get(j - start);
                if (!current.isEmpty()) {
                    boolean sizeLimitReached = currentLength + 1 + sentence.length() > chunkSize;
                    boolean semanticBoundary = cosineSimilarity(
                            currentEmbeddings.get(currentEmbeddings.size() - 1), embedding)
                            < threshold;
                    boolean tooSmall = currentLength < minChunkSize;
                    if (sizeLimitReached || (semanticBoundary && !tooSmall)) {
                        chunks.add(String.join(" ", current));
                        // 从上一块末尾回带完整句子作为重叠，避免边界处上下文丢失
                        int maxCarry = Math.min(overlap,
                                Math.max(0, chunkSize - sentence.length() - 1));
                        List<String> carried = new ArrayList<>();
                        List<List<Double>> carriedEmbeddings = new ArrayList<>();
                        int carriedLength = 0;
                        for (int k = current.size() - 1; k >= 0; k--) {
                            int sentenceLength = current.get(k).length()
                                    + (carried.isEmpty() ? 0 : 1);
                            if (carriedLength + sentenceLength > maxCarry) break;
                            carried.add(0, current.get(k));
                            carriedEmbeddings.add(0, currentEmbeddings.get(k));
                            carriedLength += sentenceLength;
                        }
                        current = carried;
                        currentEmbeddings = carriedEmbeddings;
                        currentLength = carriedLength;
                    }
                }
                if (current.isEmpty()) {
                    currentLength = sentence.length();
                } else {
                    currentLength += 1 + sentence.length();
                }
                current.add(sentence);
                currentEmbeddings.add(embedding);
            }
        }
        if (!current.isEmpty()) chunks.add(String.join(" ", current));
        return chunks;
    }

    private static double cosineSimilarity(List<Double> left, List<Double> right) {
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

    /**
     * 基于字符 bigram 的 Jaccard 相似度，用于检索后近似 chunk 去重。
     * 对中文/英文混合文本均有效，无需分词器。
     */
    private static double jaccardSimilarity(String left, String right) {
        if (left == null || right == null) return 0;
        Set<String> leftGrams = charBigrams(left);
        Set<String> rightGrams = charBigrams(right);
        if (leftGrams.isEmpty() || rightGrams.isEmpty()) {
            return left.equals(right) ? 1 : 0;
        }
        Set<String> intersection = new HashSet<>(leftGrams);
        intersection.retainAll(rightGrams);
        Set<String> union = new HashSet<>(leftGrams);
        union.addAll(rightGrams);
        return (double) intersection.size() / union.size();
    }

    private static Set<String> charBigrams(String text) {
        String normalized = text.replaceAll("\\s+", "");
        Set<String> bigrams = new HashSet<>();
        for (int i = 0; i + 1 < normalized.length(); i++) {
            bigrams.add(normalized.substring(i, i + 2));
        }
        return bigrams;
    }
}
