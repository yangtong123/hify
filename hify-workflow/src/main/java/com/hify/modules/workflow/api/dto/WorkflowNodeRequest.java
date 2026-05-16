package com.hify.modules.workflow.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkflowNodeRequest {

    @NotBlank(message = "节点 ID 不能为空")
    private String nodeId;

    @NotNull(message = "节点类型不能为空")
    private WorkflowNodeType nodeType;

    @NotBlank(message = "节点名称不能为空")
    private String name;

    private JsonNode config;

    private JsonNode position;
}
