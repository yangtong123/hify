package com.hify.modules.mcp.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class McpToolDebugRequest {

    @NotBlank(message = "工具名称不能为空")
    private String toolName;

    private Map<String, Object> arguments;
}
