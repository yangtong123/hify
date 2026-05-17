package com.hify.modules.workflow.domain.execution;

import com.hify.modules.workflow.api.dto.WorkflowEdgeType;

public record WorkflowRuntimeEdge(
        String sourceNodeId,
        String targetNodeId,
        WorkflowEdgeType edgeType,
        String conditionExpression,
        Integer priority
) {
}
