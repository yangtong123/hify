package com.hify.modules.workflow.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class WorkflowCreateRequest {

    @NotBlank(message = "工作流名称不能为空")
    private String name;

    private String description;

    private String status;

    private Integer version;

    @NotBlank(message = "开始节点 ID 不能为空")
    private String startNodeId;

    private JsonNode config;

    @Valid
    @NotEmpty(message = "节点不能为空")
    private List<WorkflowNodeRequest> nodes;

    @Valid
    private List<WorkflowEdgeRequest> edges;
}
