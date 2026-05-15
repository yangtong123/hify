package com.hify.modules.knowledge.infra.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_knowledge_base")
public class KnowledgeBasePo extends BaseEntity {

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("embedding_model_config_id")
    private Long embeddingModelConfigId;

    @TableField("embedding_dimension")
    private Integer embeddingDimension;

    @TableField("chunk_size")
    private Integer chunkSize;

    @TableField("chunk_overlap")
    private Integer chunkOverlap;

    @TableField("top_k")
    private Integer topK;

    @TableField("similarity_threshold")
    private BigDecimal similarityThreshold;

    @TableField("status")
    private String status;
}
