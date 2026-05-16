package com.hify.modules.workflow.api.dto;

import lombok.Data;

@Data
public class WorkflowEdgeResponse {

    private String sourceNodeId;

    private String targetNodeId;

    private WorkflowEdgeType edgeType;

    private String conditionExpression;

    private Integer priority;
}
