package com.hify.modules.knowledge.api;

import com.hify.common.web.PageResult;
import com.hify.modules.knowledge.api.dto.AgentKnowledgeBaseRequest;
import com.hify.modules.knowledge.api.dto.KnowledgeBaseCreateRequest;
import com.hify.modules.knowledge.api.dto.KnowledgeBaseQuery;
import com.hify.modules.knowledge.api.dto.KnowledgeBaseResponse;
import com.hify.modules.knowledge.api.dto.KnowledgeBaseUpdateRequest;
import com.hify.modules.knowledge.api.dto.KnowledgeDocumentResponse;
import com.hify.modules.knowledge.api.dto.RetrievedChunkDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KnowledgeService {

    KnowledgeBaseResponse create(KnowledgeBaseCreateRequest request);

    KnowledgeBaseResponse update(Long id, KnowledgeBaseUpdateRequest request);

    void delete(Long id);

    KnowledgeBaseResponse getById(Long id);

    PageResult<List<KnowledgeBaseResponse>> list(KnowledgeBaseQuery query);

    KnowledgeDocumentResponse uploadDocument(Long knowledgeBaseId, MultipartFile file);

    List<KnowledgeDocumentResponse> listDocuments(Long knowledgeBaseId);

    void deleteDocument(Long documentId);

    void updateAgentKnowledgeBases(Long agentId, AgentKnowledgeBaseRequest request);

    List<KnowledgeBaseResponse> listAgentKnowledgeBases(Long agentId);

    List<RetrievedChunkDto> retrieveForAgent(Long agentId, String query);
}
