package com.hify.modules.knowledge.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class KnowledgeBaseResponse {

    private Long id;

    private String name;

    private String description;

    private Long embeddingModelConfigId;

    private String embeddingModelName;

    private Integer embeddingDimension;

    private Integer chunkSize;

    private Integer chunkOverlap;

    private Integer topK;

    private BigDecimal similarityThreshold;

    private String status;

    private Integer documentCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
