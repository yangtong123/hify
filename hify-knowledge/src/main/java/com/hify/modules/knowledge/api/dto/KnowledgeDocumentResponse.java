package com.hify.modules.knowledge.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeDocumentResponse {

    private Long id;

    private Long knowledgeBaseId;

    private String fileName;

    private String fileType;

    private Long fileSize;

    private String title;

    private String processStatus;

    private Integer chunkCount;

    private String errorMessage;

    private LocalDateTime processedAt;

    private LocalDateTime createdAt;
}
