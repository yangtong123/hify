package com.hify.modules.knowledge.web;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.modules.knowledge.api.KnowledgeService;
import com.hify.modules.knowledge.api.dto.AgentKnowledgeBaseRequest;
import com.hify.modules.knowledge.api.dto.KnowledgeBaseCreateRequest;
import com.hify.modules.knowledge.api.dto.KnowledgeBaseQuery;
import com.hify.modules.knowledge.api.dto.KnowledgeBaseResponse;
import com.hify.modules.knowledge.api.dto.KnowledgeBaseUpdateRequest;
import com.hify.modules.knowledge.api.dto.KnowledgeDocumentResponse;
import com.hify.modules.knowledge.api.dto.RetrievedChunkDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @PostMapping
    public Result<KnowledgeBaseResponse> create(@Valid @RequestBody KnowledgeBaseCreateRequest request) {
        return Result.ok(knowledgeService.create(request));
    }

    @PutMapping("/{id}")
    public Result<KnowledgeBaseResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody KnowledgeBaseUpdateRequest request) {
        return Result.ok(knowledgeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result<KnowledgeBaseResponse> getById(@PathVariable Long id) {
        return Result.ok(knowledgeService.getById(id));
    }

    @GetMapping
    public PageResult<List<KnowledgeBaseResponse>> list(@Valid KnowledgeBaseQuery query) {
        return knowledgeService.list(query);
    }

    @PostMapping("/{id}/documents")
    public Result<KnowledgeDocumentResponse> uploadDocument(@PathVariable Long id,
                                                            @RequestParam("file") MultipartFile file) {
        return Result.ok(knowledgeService.uploadDocument(id, file));
    }

    @GetMapping("/{id}/documents")
    public Result<List<KnowledgeDocumentResponse>> listDocuments(@PathVariable Long id) {
        return Result.ok(knowledgeService.listDocuments(id));
    }

    @DeleteMapping("/documents/{documentId}")
    public Result<Void> deleteDocument(@PathVariable Long documentId) {
        knowledgeService.deleteDocument(documentId);
        return Result.ok();
    }

    @PutMapping("/agents/{agentId}")
    public Result<Void> updateAgentKnowledgeBases(@PathVariable Long agentId,
                                                  @RequestBody AgentKnowledgeBaseRequest request) {
        knowledgeService.updateAgentKnowledgeBases(agentId, request);
        return Result.ok();
    }

    @GetMapping("/agents/{agentId}")
    public Result<List<KnowledgeBaseResponse>> listAgentKnowledgeBases(@PathVariable Long agentId) {
        return Result.ok(knowledgeService.listAgentKnowledgeBases(agentId));
    }

    @GetMapping("/agents/{agentId}/retrieve")
    public Result<List<RetrievedChunkDto>> retrieve(@PathVariable Long agentId,
                                                    @RequestParam String query) {
        return Result.ok(knowledgeService.retrieveForAgent(agentId, query));
    }
}
