package com.hify.modules.knowledge.web;

import com.hify.common.web.Result;
import com.hify.modules.knowledge.api.KnowledgeService;
import com.hify.modules.knowledge.api.dto.KnowledgeDocumentResponse;
import com.hify.modules.knowledge.api.dto.RetrievedChunkDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class KnowledgeDocumentController {

    private final KnowledgeService knowledgeService;

    @GetMapping("/{documentId}")
    public Result<KnowledgeDocumentResponse> getDocument(@PathVariable Long documentId) {
        return Result.ok(knowledgeService.getDocument(documentId));
    }

    @GetMapping("/{documentId}/chunks")
    public Result<List<RetrievedChunkDto>> listDocumentChunks(@PathVariable Long documentId) {
        return Result.ok(knowledgeService.listDocumentChunks(documentId));
    }

    @DeleteMapping("/{documentId}")
    public Result<Void> deleteDocument(@PathVariable Long documentId) {
        knowledgeService.deleteDocument(documentId);
        return Result.ok();
    }
}
