package com.hify.modules.knowledge.infra.vector;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class KnowledgeChunkRecord {

    private Long id;

    private Long knowledgeBaseId;

    private Long documentId;

    private Integer chunkIndex;

    private String content;

    private Integer tokenCount;

    private Integer charCount;

    private Integer pageNumber;

    private String sectionTitle;

    private List<Double> embedding;

    private Long embeddingModelConfigId;

    private String embeddingModel;

    private String contentHash;

    private Double distance;

    private LocalDateTime createdAt;
}
