package com.hify.modules.knowledge.api.dto;

import lombok.Data;

@Data
public class RetrievedChunkDto {

    private Long chunkId;

    private Long knowledgeBaseId;

    private Long documentId;

    private String documentTitle;

    private String fileName;

    private Integer chunkIndex;

    private String content;

    private Integer tokenCount;

    private Integer pageNumber;

    private String sectionTitle;

    private Double distance;

    private Double similarity;
}
