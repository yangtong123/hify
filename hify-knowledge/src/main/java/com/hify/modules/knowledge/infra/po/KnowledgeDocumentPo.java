package com.hify.modules.knowledge.infra.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_knowledge_document")
public class KnowledgeDocumentPo extends BaseEntity {

    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    @TableField("file_name")
    private String fileName;

    @TableField("file_type")
    private String fileType;

    @TableField("file_size")
    private Long fileSize;

    @TableField("storage_path")
    private String storagePath;

    @TableField("content_hash")
    private String contentHash;

    @TableField("title")
    private String title;

    @TableField("process_status")
    private String processStatus;

    @TableField("chunk_count")
    private Integer chunkCount;

    @TableField("error_message")
    private String errorMessage;

    @TableField("processed_at")
    private LocalDateTime processedAt;
}
