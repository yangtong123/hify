package com.hify.modules.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkflowEdgeRequest {

    @NotBlank(message = "连接起点节点 ID 不能为空")
    private String sourceNodeId;

    @NotBlank(message = "连接终点节点 ID 不能为空")
    private String targetNodeId;

    private WorkflowEdgeType edgeType = WorkflowEdgeType.NORMAL;

    private String conditionExpression;

    private Integer priority = 0;
}
