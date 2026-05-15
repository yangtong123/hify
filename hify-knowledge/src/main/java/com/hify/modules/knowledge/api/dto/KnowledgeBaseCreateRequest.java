package com.hify.modules.knowledge.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class KnowledgeBaseCreateRequest {

    @NotBlank(message = "知识库名称不能为空")
    private String name;

    private String description;

    @NotNull(message = "Embedding 模型配置不能为空")
    private Long embeddingModelConfigId;

    @NotNull(message = "向量维度不能为空")
    private Integer embeddingDimension;

    @Min(value = 100, message = "chunkSize 不能小于 100")
    @Max(value = 4000, message = "chunkSize 不能大于 4000")
    private Integer chunkSize;

    @Min(value = 0, message = "chunkOverlap 不能小于 0")
    @Max(value = 1000, message = "chunkOverlap 不能大于 1000")
    private Integer chunkOverlap;

    @Min(value = 1, message = "topK 不能小于 1")
    @Max(value = 20, message = "topK 不能大于 20")
    private Integer topK;

    @DecimalMin(value = "0.0000", message = "similarityThreshold 不能小于 0")
    @DecimalMax(value = "1.0000", message = "similarityThreshold 不能大于 1")
    private BigDecimal similarityThreshold;

    private String status;
}
