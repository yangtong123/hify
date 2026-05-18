package com.hify.modules.mcp.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class McpServerResponse {

    private Long id;

    private String name;

    private String endpoint;

    private Integer enabled;

    private Integer toolCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
