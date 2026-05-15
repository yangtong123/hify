package com.hify.modules.knowledge.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.modules.knowledge.api.KnowledgeService;
import com.hify.modules.knowledge.api.dto.AgentKnowledgeBaseRequest;
import com.hify.modules.knowledge.api.dto.KnowledgeBaseCreateRequest;
import com.hify.modules.knowledge.api.dto.KnowledgeBaseQuery;
import com.hify.modules.knowledge.api.dto.KnowledgeBaseResponse;
import com.hify.modules.knowledge.api.dto.KnowledgeBaseUpdateRequest;
import com.hify.modules.knowledge.api.dto.KnowledgeDocumentResponse;
import com.hify.modules.knowledge.api.dto.RetrievedChunkDto;
import com.hify.modules.knowledge.infra.mapper.AgentKnowledgeBaseMapper;
import com.hify.modules.knowledge.infra.mapper.KnowledgeBaseMapper;
import com.hify.modules.knowledge.infra.mapper.KnowledgeDocumentMapper;
import com.hify.modules.knowledge.infra.po.AgentKnowledgeBasePo;
import com.hify.modules.knowledge.infra.po.KnowledgeBasePo;
import com.hify.modules.knowledge.infra.po.KnowledgeDocumentPo;
import com.hify.modules.knowledge.infra.vector.KnowledgeChunkRecord;
import com.hify.modules.knowledge.infra.vector.KnowledgeVectorRepository;
import com.hify.modules.provider.api.ProviderService;
import com.hify.modules.provider.api.dto.EmbeddingRequest;
import com.hify.modules.provider.api.dto.EmbeddingResponse;
import com.hify.modules.provider.api.dto.ModelConfigDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PARSING = "PARSING";
    private static final String STATUS_CHUNKING = "CHUNKING";
    private static final String STATUS_EMBEDDING = "EMBEDDING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final int DEFAULT_CHUNK_SIZE = 1000;
    private static final int DEFAULT_CHUNK_OVERLAP = 150;
    private static final int DEFAULT_TOP_K = 5;
    private static final BigDecimal DEFAULT_SIMILARITY_THRESHOLD = new BigDecimal("0.7000");
    private static final Set<String> SUPPORTED_TEXT_TYPES = Set.of("txt", "md", "markdown", "html", "htm", "csv", "json");

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final AgentKnowledgeBaseMapper agentKnowledgeBaseMapper;
    private final ProviderService providerService;
    private final KnowledgeVectorRepository vectorRepository;
    private final Executor asyncExecutor;

    @Value("${hify.knowledge.storage-path:data/knowledge}")
    private String storagePath;

    public KnowledgeServiceImpl(KnowledgeBaseMapper knowledgeBaseMapper,
                                KnowledgeDocumentMapper documentMapper,
                                AgentKnowledgeBaseMapper agentKnowledgeBaseMapper,
                                ProviderService providerService,
                                KnowledgeVectorRepository vectorRepository,
                                @Qualifier("asyncExecutor") Executor asyncExecutor) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.documentMapper = documentMapper;
        this.agentKnowledgeBaseMapper = agentKnowledgeBaseMapper;
        this.providerService = providerService;
        this.vectorRepository = vectorRepository;
        this.asyncExecutor = asyncExecutor;
    }

    @Override
    @Transactional
    public KnowledgeBaseResponse create(KnowledgeBaseCreateRequest request) {
        log.info("Knowledge base create started: name={}, embeddingModelConfigId={}, dimension={}",
                request.getName(), request.getEmbeddingModelConfigId(), request.getEmbeddingDimension());
        checkNameDuplicate(request.getName(), null);
        validateEmbeddingModel(request.getEmbeddingModelConfigId());

        KnowledgeBasePo po = new KnowledgeBasePo();
        applyRequest(po, request);
        knowledgeBaseMapper.insert(po);
        log.info("Knowledge base created: id={}, name={}, embeddingModelConfigId={}, chunkSize={}, chunkOverlap={}, topK={}",
                po.getId(), po.getName(), po.getEmbeddingModelConfigId(), po.getChunkSize(),
                po.getChunkOverlap(), po.getTopK());
        return toResponse(po, null);
    }

    @Override
    @Transactional
    public KnowledgeBaseResponse update(Long id, KnowledgeBaseUpdateRequest request) {
        log.info("Knowledge base update started: id={}, name={}, embeddingModelConfigId={}, dimension={}",
                id, request.getName(), request.getEmbeddingModelConfigId(), request.getEmbeddingDimension());
        KnowledgeBasePo po = requireKnowledgeBase(id);
        checkNameDuplicate(request.getName(), id);
        validateEmbeddingModel(request.getEmbeddingModelConfigId());
        applyRequest(po, request);
        po.setUpdatedAt(null);
        knowledgeBaseMapper.updateById(po);
        log.info("Knowledge base updated: id={}, name={}, status={}, chunkSize={}, chunkOverlap={}, topK={}",
                po.getId(), po.getName(), po.getStatus(), po.getChunkSize(), po.getChunkOverlap(), po.getTopK());
        return toResponse(po, null);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        KnowledgeBasePo po = requireKnowledgeBase(id);
        knowledgeBaseMapper.deleteById(id);
        log.info("Knowledge base deleted: id={}, name={}", id, po.getName());
    }

    @Override
    public KnowledgeBaseResponse getById(Long id) {
        return toResponse(requireKnowledgeBase(id), null);
    }

    @Override
    public PageResult<List<KnowledgeBaseResponse>> list(KnowledgeBaseQuery query) {
        LambdaQueryWrapper<KnowledgeBasePo> wrapper = new LambdaQueryWrapper<KnowledgeBasePo>()
                .like(StringUtils.hasText(query.getName()), KnowledgeBasePo::getName, query.getName())
                .eq(StringUtils.hasText(query.getStatus()), KnowledgeBasePo::getStatus, query.getStatus())
                .orderByDesc(KnowledgeBasePo::getCreatedAt);
        Page<KnowledgeBasePo> page = PageHelper.toPage(query.getPage(), query.getSize());
        IPage<KnowledgeBasePo> result = knowledgeBaseMapper.selectPage(page, wrapper);
        Map<Long, Integer> documentCounts = countDocuments(result.getRecords().stream().map(KnowledgeBasePo::getId).toList());
        List<KnowledgeBaseResponse> records = result.getRecords().stream()
                .map(item -> toResponse(item, documentCounts.get(item.getId())))
                .toList();
        return PageResult.ok(records, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    @Transactional
    public KnowledgeDocumentResponse uploadDocument(Long knowledgeBaseId, MultipartFile file) {
        log.info("Knowledge document upload started: knowledgeBaseId={}, fileName={}, size={}",
                knowledgeBaseId, file != null ? file.getOriginalFilename() : null,
                file != null ? file.getSize() : null);
        KnowledgeBasePo knowledgeBase = requireActiveKnowledgeBase(knowledgeBaseId);
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "上传文件不能为空");
        }
        String fileName = Objects.requireNonNullElse(file.getOriginalFilename(), "document.txt");
        String fileType = getFileType(fileName);
        if (!SUPPORTED_TEXT_TYPES.contains(fileType)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "当前仅支持 UTF-8 文本文档: txt/md/html/csv/json");
        }

        byte[] bytes = readBytes(file);
        String contentHash = sha256(bytes);
        KnowledgeDocumentPo existing = documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocumentPo>()
                .eq(KnowledgeDocumentPo::getKnowledgeBaseId, knowledgeBaseId)
                .eq(KnowledgeDocumentPo::getContentHash, contentHash));
        if (existing != null) {
            log.info("Knowledge document upload deduplicated: knowledgeBaseId={}, documentId={}, fileName={}",
                    knowledgeBaseId, existing.getId(), fileName);
            return toDocumentResponse(existing);
        }

        Path savedPath = saveFile(knowledgeBaseId, fileName, bytes);
        KnowledgeDocumentPo document = new KnowledgeDocumentPo();
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setFileName(fileName);
        document.setFileType(fileType.toUpperCase());
        document.setFileSize((long) bytes.length);
        document.setStoragePath(savedPath.toString());
        document.setContentHash(contentHash);
        document.setTitle(stripExtension(fileName));
        document.setProcessStatus(STATUS_PENDING);
        document.setChunkCount(0);
        documentMapper.insert(document);

        log.info("Knowledge document uploaded: documentId={}, knowledgeBaseId={}, fileName={}, fileType={}, size={}",
                document.getId(), knowledgeBaseId, fileName, document.getFileType(), document.getFileSize());
        scheduleProcessDocument(knowledgeBase, document, bytes);
        return toDocumentResponse(document);
    }

    @Override
    public List<KnowledgeDocumentResponse> listDocuments(Long knowledgeBaseId) {
        requireKnowledgeBase(knowledgeBaseId);
        return documentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocumentPo>()
                        .eq(KnowledgeDocumentPo::getKnowledgeBaseId, knowledgeBaseId)
                        .orderByDesc(KnowledgeDocumentPo::getCreatedAt))
                .stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteDocument(Long documentId) {
        KnowledgeDocumentPo document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        documentMapper.deleteById(documentId);
        vectorRepository.deleteDocumentChunks(documentId);
        log.info("Knowledge document deleted: documentId={}, knowledgeBaseId={}, fileName={}",
                documentId, document.getKnowledgeBaseId(), document.getFileName());
    }

    @Override
    @Transactional
    public void updateAgentKnowledgeBases(Long agentId, AgentKnowledgeBaseRequest request) {
        List<Long> knowledgeBaseIds = request != null && request.getKnowledgeBaseIds() != null
                ? request.getKnowledgeBaseIds()
                : List.of();
        log.info("Agent knowledge base update started: agentId={}, requestedKnowledgeBases={}",
                agentId, knowledgeBaseIds.size());
        List<Long> distinctIds = knowledgeBaseIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        validateKnowledgeBaseIds(distinctIds);

        List<AgentKnowledgeBasePo> existing = agentKnowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<AgentKnowledgeBasePo>().eq(AgentKnowledgeBasePo::getAgentId, agentId));
        for (AgentKnowledgeBasePo mapping : existing) {
            agentKnowledgeBaseMapper.deleteById(mapping.getId());
        }

        int priority = 100;
        for (Long knowledgeBaseId : distinctIds) {
            AgentKnowledgeBasePo mapping = new AgentKnowledgeBasePo();
            mapping.setAgentId(agentId);
            mapping.setKnowledgeBaseId(knowledgeBaseId);
            mapping.setPriority(priority);
            mapping.setEnabled(1);
            agentKnowledgeBaseMapper.insert(mapping);
            priority += 100;
        }
        log.info("Agent knowledge base updated: agentId={}, knowledgeBases={}", agentId, distinctIds.size());
    }

    @Override
    public List<KnowledgeBaseResponse> listAgentKnowledgeBases(Long agentId) {
        List<AgentKnowledgeBasePo> mappings = agentKnowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<AgentKnowledgeBasePo>()
                        .eq(AgentKnowledgeBasePo::getAgentId, agentId)
                        .eq(AgentKnowledgeBasePo::getEnabled, 1)
                        .orderByAsc(AgentKnowledgeBasePo::getPriority));
        if (mappings.isEmpty()) {
            return List.of();
        }
        List<Long> ids = mappings.stream().map(AgentKnowledgeBasePo::getKnowledgeBaseId).toList();
        Map<Long, KnowledgeBasePo> baseMap = knowledgeBaseMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(KnowledgeBasePo::getId, item -> item));
        return ids.stream()
                .map(baseMap::get)
                .filter(Objects::nonNull)
                .map(item -> toResponse(item, null))
                .toList();
    }

    @Override
    public List<RetrievedChunkDto> retrieveForAgent(Long agentId, String query) {
        long start = System.currentTimeMillis();
        if (!StringUtils.hasText(query)) {
            log.info("Knowledge retrieval skipped: agentId={}, reason=empty_query", agentId);
            return List.of();
        }
        List<KnowledgeBasePo> knowledgeBases = listAgentKnowledgeBasePos(agentId);
        if (knowledgeBases.isEmpty()) {
            log.info("Knowledge retrieval skipped: agentId={}, reason=no_active_knowledge_base", agentId);
            return List.of();
        }

        int topK = knowledgeBases.stream().map(KnowledgeBasePo::getTopK).filter(Objects::nonNull).max(Integer::compareTo).orElse(DEFAULT_TOP_K);
        double threshold = knowledgeBases.stream()
                .map(KnowledgeBasePo::getSimilarityThreshold)
                .filter(Objects::nonNull)
                .map(BigDecimal::doubleValue)
                .min(Double::compareTo)
                .orElse(DEFAULT_SIMILARITY_THRESHOLD.doubleValue());
        Long embeddingModelConfigId = knowledgeBases.get(0).getEmbeddingModelConfigId();
        int embeddingDimension = knowledgeBases.get(0).getEmbeddingDimension();

        List<Double> queryEmbedding = embed(embeddingModelConfigId, List.of(query), embeddingDimension).get(0);
        List<Long> knowledgeBaseIds = knowledgeBases.stream().map(KnowledgeBasePo::getId).toList();
        List<KnowledgeChunkRecord> records = vectorRepository.search(knowledgeBaseIds, queryEmbedding, topK, threshold);
        List<RetrievedChunkDto> chunks = enrichRetrievedChunks(records);
        log.info("Knowledge retrieval completed: agentId={}, knowledgeBases={}, topK={}, threshold={}, hits={}, latency={}ms",
                agentId, knowledgeBaseIds.size(), topK, threshold, chunks.size(), System.currentTimeMillis() - start);
        return chunks;
    }

    private void scheduleProcessDocument(KnowledgeBasePo knowledgeBase, KnowledgeDocumentPo document, byte[] bytes) {
        Runnable task = () -> processDocument(knowledgeBase, document, bytes);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.info("Knowledge document async processing scheduled after commit: documentId={}, knowledgeBaseId={}",
                            document.getId(), knowledgeBase.getId());
                    asyncExecutor.execute(task);
                }
            });
        } else {
            log.info("Knowledge document async processing scheduled: documentId={}, knowledgeBaseId={}",
                    document.getId(), knowledgeBase.getId());
            asyncExecutor.execute(task);
        }
    }

    private void processDocument(KnowledgeBasePo knowledgeBase, KnowledgeDocumentPo document, byte[] bytes) {
        try {
            log.info("Knowledge document processing started: documentId={}, knowledgeBaseId={}",
                    document.getId(), knowledgeBase.getId());
            updateDocumentStatus(document, STATUS_PARSING, null, null);
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (!StringUtils.hasText(text)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "文档内容为空");
            }

            updateDocumentStatus(document, STATUS_CHUNKING, null, null);
            List<String> chunks = splitText(text, knowledgeBase.getChunkSize(), knowledgeBase.getChunkOverlap());
            if (chunks.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "文档未生成有效分块");
            }
            log.info("Knowledge document chunked: documentId={}, knowledgeBaseId={}, chunks={}, chunkSize={}, chunkOverlap={}",
                    document.getId(), knowledgeBase.getId(), chunks.size(), knowledgeBase.getChunkSize(),
                    knowledgeBase.getChunkOverlap());

            updateDocumentStatus(document, STATUS_EMBEDDING, null, null);
            List<List<Double>> embeddings = embed(knowledgeBase.getEmbeddingModelConfigId(), chunks, knowledgeBase.getEmbeddingDimension());
            ModelConfigDto embeddingModel = providerService.getModelConfig(knowledgeBase.getEmbeddingModelConfigId());
            List<KnowledgeChunkRecord> records = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                String content = chunks.get(i);
                KnowledgeChunkRecord record = new KnowledgeChunkRecord();
                record.setKnowledgeBaseId(knowledgeBase.getId());
                record.setDocumentId(document.getId());
                record.setChunkIndex(i);
                record.setContent(content);
                record.setTokenCount(estimateTokens(content));
                record.setCharCount(content.length());
                record.setEmbedding(embeddings.get(i));
                record.setEmbeddingModelConfigId(knowledgeBase.getEmbeddingModelConfigId());
                record.setEmbeddingModel(embeddingModel.getModelId());
                record.setContentHash(sha256((document.getContentHash() + ":" + i + ":" + content).getBytes(StandardCharsets.UTF_8)));
                records.add(record);
            }
            vectorRepository.replaceDocumentChunks(document.getId(), records, knowledgeBase.getEmbeddingDimension());
            updateDocumentStatus(document, STATUS_COMPLETED, records.size(), null);
            log.info("Knowledge document processed: documentId={}, knowledgeBaseId={}, chunks={}",
                    document.getId(), knowledgeBase.getId(), records.size());
        } catch (RuntimeException e) {
            updateDocumentStatus(document, STATUS_FAILED, 0, e.getMessage());
            log.warn("Knowledge document processing failed: documentId={}, knowledgeBaseId={}, error={}",
                    document.getId(), knowledgeBase.getId(), e.getMessage());
            throw e;
        }
    }

    private List<List<Double>> embed(Long modelConfigId, List<String> inputs, int expectedDimension) {
        long start = System.currentTimeMillis();
        log.info("Knowledge embedding started: modelConfigId={}, inputs={}, expectedDimension={}",
                modelConfigId, inputs != null ? inputs.size() : 0, expectedDimension);
        EmbeddingRequest request = new EmbeddingRequest();
        request.setInputs(inputs);
        EmbeddingResponse response = providerService.embed(modelConfigId, request);
        List<List<Double>> embeddings = response.getEmbeddings();
        if (embeddings == null || embeddings.size() != inputs.size()) {
            throw new BizException(ErrorCode.THIRD_PARTY_ERROR, "Embedding 返回数量与输入不一致");
        }
        for (List<Double> embedding : embeddings) {
            if (embedding == null || embedding.size() != expectedDimension) {
                throw new BizException(ErrorCode.PARAM_ERROR, "Embedding 维度与知识库配置不一致");
            }
        }
        log.info("Knowledge embedding completed: modelConfigId={}, inputs={}, embeddings={}, latency={}ms",
                modelConfigId, inputs.size(), embeddings.size(), System.currentTimeMillis() - start);
        return embeddings;
    }

    private List<RetrievedChunkDto> enrichRetrievedChunks(List<KnowledgeChunkRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        List<Long> documentIds = records.stream().map(KnowledgeChunkRecord::getDocumentId).distinct().toList();
        Map<Long, KnowledgeDocumentPo> documentMap = documentMapper.selectBatchIds(documentIds).stream()
                .collect(Collectors.toMap(KnowledgeDocumentPo::getId, item -> item));
        List<RetrievedChunkDto> chunks = new ArrayList<>();
        for (KnowledgeChunkRecord record : records) {
            KnowledgeDocumentPo document = documentMap.get(record.getDocumentId());
            RetrievedChunkDto dto = new RetrievedChunkDto();
            dto.setChunkId(record.getId());
            dto.setKnowledgeBaseId(record.getKnowledgeBaseId());
            dto.setDocumentId(record.getDocumentId());
            dto.setChunkIndex(record.getChunkIndex());
            dto.setContent(record.getContent());
            dto.setTokenCount(record.getTokenCount());
            dto.setPageNumber(record.getPageNumber());
            dto.setSectionTitle(record.getSectionTitle());
            dto.setDistance(record.getDistance());
            dto.setSimilarity(1.0D - record.getDistance());
            if (document != null) {
                dto.setDocumentTitle(document.getTitle());
                dto.setFileName(document.getFileName());
            }
            chunks.add(dto);
        }
        return chunks;
    }

    private List<KnowledgeBasePo> listAgentKnowledgeBasePos(Long agentId) {
        List<AgentKnowledgeBasePo> mappings = agentKnowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<AgentKnowledgeBasePo>()
                        .eq(AgentKnowledgeBasePo::getAgentId, agentId)
                        .eq(AgentKnowledgeBasePo::getEnabled, 1)
                        .orderByAsc(AgentKnowledgeBasePo::getPriority));
        if (mappings.isEmpty()) {
            return List.of();
        }
        List<Long> ids = mappings.stream().map(AgentKnowledgeBasePo::getKnowledgeBaseId).toList();
        Map<Long, KnowledgeBasePo> baseMap = knowledgeBaseMapper.selectBatchIds(ids).stream()
                .filter(item -> STATUS_ACTIVE.equals(item.getStatus()))
                .collect(Collectors.toMap(KnowledgeBasePo::getId, item -> item));
        List<KnowledgeBasePo> bases = new ArrayList<>();
        for (Long id : ids) {
            KnowledgeBasePo item = baseMap.get(id);
            if (item != null) {
                bases.add(item);
            }
        }
        return bases;
    }

    private void updateDocumentStatus(KnowledgeDocumentPo document, String status, Integer chunkCount, String errorMessage) {
        KnowledgeDocumentPo update = new KnowledgeDocumentPo();
        update.setId(document.getId());
        update.setProcessStatus(status);
        update.setChunkCount(chunkCount);
        update.setErrorMessage(errorMessage);
        update.setProcessedAt(STATUS_COMPLETED.equals(status) ? LocalDateTime.now() : null);
        update.setUpdatedAt(null);
        documentMapper.updateById(update);
        document.setProcessStatus(status);
        document.setChunkCount(chunkCount);
        document.setErrorMessage(errorMessage);
        log.info("Knowledge document status updated: documentId={}, status={}, chunkCount={}, hasError={}",
                document.getId(), status, chunkCount, StringUtils.hasText(errorMessage));
    }

    private void applyRequest(KnowledgeBasePo po, KnowledgeBaseCreateRequest request) {
        po.setName(request.getName());
        po.setDescription(Objects.requireNonNullElse(request.getDescription(), ""));
        po.setEmbeddingModelConfigId(request.getEmbeddingModelConfigId());
        po.setEmbeddingDimension(request.getEmbeddingDimension());
        po.setChunkSize(defaultIfNull(request.getChunkSize(), DEFAULT_CHUNK_SIZE));
        po.setChunkOverlap(defaultIfNull(request.getChunkOverlap(), DEFAULT_CHUNK_OVERLAP));
        if (po.getChunkOverlap() >= po.getChunkSize()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "chunkOverlap 必须小于 chunkSize");
        }
        po.setTopK(defaultIfNull(request.getTopK(), DEFAULT_TOP_K));
        po.setSimilarityThreshold(defaultIfNull(request.getSimilarityThreshold(), DEFAULT_SIMILARITY_THRESHOLD));
        po.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus().toUpperCase() : STATUS_ACTIVE);
    }

    private void validateEmbeddingModel(Long modelConfigId) {
        providerService.getModelConfig(modelConfigId);
    }

    private void validateKnowledgeBaseIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        Set<Long> found = knowledgeBaseMapper.selectBatchIds(ids).stream()
                .map(KnowledgeBasePo::getId)
                .collect(Collectors.toSet());
        for (Long id : ids) {
            if (!found.contains(id)) {
                throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在: " + id);
            }
        }
    }

    private KnowledgeBasePo requireKnowledgeBase(Long id) {
        KnowledgeBasePo po = knowledgeBaseMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
        return po;
    }

    private KnowledgeBasePo requireActiveKnowledgeBase(Long id) {
        KnowledgeBasePo po = requireKnowledgeBase(id);
        if (!STATUS_ACTIVE.equals(po.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "知识库未启用");
        }
        return po;
    }

    private void checkNameDuplicate(String name, Long excludeId) {
        LambdaQueryWrapper<KnowledgeBasePo> wrapper = new LambdaQueryWrapper<KnowledgeBasePo>()
                .eq(KnowledgeBasePo::getName, name);
        if (excludeId != null) {
            wrapper.ne(KnowledgeBasePo::getId, excludeId);
        }
        if (knowledgeBaseMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "知识库名称已存在: " + name);
        }
    }

    private Map<Long, Integer> countDocuments(List<Long> knowledgeBaseIds) {
        if (knowledgeBaseIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Integer> counts = new HashMap<>();
        for (KnowledgeDocumentPo document : documentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocumentPo>().in(KnowledgeDocumentPo::getKnowledgeBaseId, knowledgeBaseIds))) {
            counts.merge(document.getKnowledgeBaseId(), 1, Integer::sum);
        }
        return counts;
    }

    private KnowledgeBaseResponse toResponse(KnowledgeBasePo po, Integer documentCount) {
        KnowledgeBaseResponse response = new KnowledgeBaseResponse();
        response.setId(po.getId());
        response.setName(po.getName());
        response.setDescription(po.getDescription());
        response.setEmbeddingModelConfigId(po.getEmbeddingModelConfigId());
        response.setEmbeddingDimension(po.getEmbeddingDimension());
        response.setChunkSize(po.getChunkSize());
        response.setChunkOverlap(po.getChunkOverlap());
        response.setTopK(po.getTopK());
        response.setSimilarityThreshold(po.getSimilarityThreshold());
        response.setStatus(po.getStatus());
        response.setDocumentCount(documentCount);
        response.setCreatedAt(po.getCreatedAt());
        response.setUpdatedAt(po.getUpdatedAt());
        try {
            ModelConfigDto model = providerService.getModelConfig(po.getEmbeddingModelConfigId());
            response.setEmbeddingModelName(model.getName());
        } catch (RuntimeException e) {
            response.setEmbeddingModelName(null);
        }
        return response;
    }

    private KnowledgeDocumentResponse toDocumentResponse(KnowledgeDocumentPo po) {
        KnowledgeDocumentResponse response = new KnowledgeDocumentResponse();
        response.setId(po.getId());
        response.setKnowledgeBaseId(po.getKnowledgeBaseId());
        response.setFileName(po.getFileName());
        response.setFileType(po.getFileType());
        response.setFileSize(po.getFileSize());
        response.setTitle(po.getTitle());
        response.setProcessStatus(po.getProcessStatus());
        response.setChunkCount(po.getChunkCount());
        response.setErrorMessage(po.getErrorMessage());
        response.setProcessedAt(po.getProcessedAt());
        response.setCreatedAt(po.getCreatedAt());
        return response;
    }

    private List<String> splitText(String text, int chunkSize, int chunkOverlap) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());
            int adjustedEnd = adjustEnd(normalized, start, end);
            String chunk = normalized.substring(start, adjustedEnd).trim();
            if (StringUtils.hasText(chunk)) {
                chunks.add(chunk);
            }
            if (adjustedEnd >= normalized.length()) {
                break;
            }
            start = Math.max(adjustedEnd - chunkOverlap, start + 1);
        }
        return chunks;
    }

    private int adjustEnd(String text, int start, int end) {
        if (end >= text.length()) {
            return end;
        }
        int paragraphBreak = text.lastIndexOf("\n\n", end);
        if (paragraphBreak > start + 200) {
            return paragraphBreak;
        }
        int lineBreak = text.lastIndexOf('\n', end);
        if (lineBreak > start + 200) {
            return lineBreak;
        }
        return end;
    }

    private Path saveFile(Long knowledgeBaseId, String fileName, byte[] bytes) {
        try {
            Path dir = Path.of(storagePath, String.valueOf(knowledgeBaseId));
            Files.createDirectories(dir);
            Path file = dir.resolve(System.currentTimeMillis() + "-" + fileName.replaceAll("[/\\\\]", "_"));
            Files.write(file, bytes);
            return file;
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "保存文件失败: " + e.getMessage());
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "读取上传文件失败: " + e.getMessage());
        }
    }

    private String getFileType(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index >= 0 ? fileName.substring(index + 1).toLowerCase() : "txt";
    }

    private String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(bytes);
            StringBuilder builder = new StringBuilder();
            for (byte value : hashed) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "计算文件 Hash 失败: " + e.getMessage());
        }
    }

    private int estimateTokens(String content) {
        return Math.max(1, content.length() / 2);
    }

    private <T> T defaultIfNull(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }
}
