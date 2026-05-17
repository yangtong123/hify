package com.hify.modules.workflow.domain.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.hify.modules.workflow.api.dto.WorkflowNodeType;

public record WorkflowRuntimeNode(
        String nodeId,
        WorkflowNodeType nodeType,
        String name,
        JsonNode configJson
) {
}
