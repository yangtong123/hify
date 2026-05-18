package com.hify.modules.mcp.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class McpServerRequest {

    @NotBlank(message = "MCP Server 名称不能为空")
    private String name;

    @NotBlank(message = "MCP Server endpoint 不能为空")
    private String endpoint;

    private Integer enabled;
}
