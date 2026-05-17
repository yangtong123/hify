package com.hify.modules.workflow.infra.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_workflow_run", autoResultMap = true)
public class WorkflowRunPo extends BaseEntity {

    @TableField("workflow_id")
    private Long workflowId;

    @TableField("workflow_version")
    private Integer workflowVersion;

    @TableField("user_id")
    private String userId;

    @TableField("status")
    private String status;

    @TableField(value = "inputs_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> inputsJson;

    @TableField(value = "outputs_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> outputsJson;

    @TableField("error_message")
    private String errorMessage;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("elapsed_ms")
    private Long elapsedMs;
}
