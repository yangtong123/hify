package com.hify.modules.mcp.api.dto;

import lombok.Data;

import java.util.Map;

@Data
public class McpToolDto {

    private Long id;

    private Long mcpServerId;

    private String name;

    private String description;

    private Map<String, Object> inputSchema;
}
