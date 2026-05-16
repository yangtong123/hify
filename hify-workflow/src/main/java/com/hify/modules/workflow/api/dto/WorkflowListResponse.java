package com.hify.modules.workflow.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkflowListResponse {

    private Long id;

    private String name;

    private String description;

    private String status;

    private Integer version;

    private String startNodeId;

    private JsonNode config;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
