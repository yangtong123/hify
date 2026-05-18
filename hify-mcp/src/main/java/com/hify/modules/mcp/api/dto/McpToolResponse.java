package com.hify.modules.mcp.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class McpToolResponse {

    private Long id;

    private Long mcpServerId;

    private String name;

    private String description;

    private Map<String, Object> inputSchema;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
