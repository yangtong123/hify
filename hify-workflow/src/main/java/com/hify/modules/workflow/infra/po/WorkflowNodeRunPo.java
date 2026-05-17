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
@TableName(value = "t_workflow_node_run", autoResultMap = true)
public class WorkflowNodeRunPo extends BaseEntity {

    @TableField("workflow_run_id")
    private Long workflowRunId;

    @TableField("workflow_id")
    private Long workflowId;

    @TableField("node_id")
    private String nodeId;

    @TableField("node_type")
    private String nodeType;

    @TableField("status")
    private String status;

    @TableField(value = "input_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> inputJson;

    @TableField(value = "output_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> outputJson;

    @TableField("error_message")
    private String errorMessage;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("elapsed_ms")
    private Long elapsedMs;
}
