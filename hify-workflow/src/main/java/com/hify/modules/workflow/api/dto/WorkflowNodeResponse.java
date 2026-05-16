package com.hify.modules.workflow.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class WorkflowNodeResponse {

    private String nodeId;

    private WorkflowNodeType nodeType;

    private String name;

    private JsonNode config;

    private JsonNode position;
}
