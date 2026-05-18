package com.hify.modules.mcp.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class McpConnectionTestResult {

    private boolean success;

    private String message;

    private Long latencyMs;

    private Integer toolCount;

    private List<String> tools;

    public static McpConnectionTestResult success(long latencyMs, List<String> tools) {
        McpConnectionTestResult result = new McpConnectionTestResult();
        result.setSuccess(true);
        result.setMessage("连接成功");
        result.setLatencyMs(latencyMs);
        result.setToolCount(tools != null ? tools.size() : 0);
        result.setTools(tools);
        return result;
    }

    public static McpConnectionTestResult fail(long latencyMs, String message) {
        McpConnectionTestResult result = new McpConnectionTestResult();
        result.setSuccess(false);
        result.setMessage(message);
        result.setLatencyMs(latencyMs);
        result.setToolCount(0);
        result.setTools(List.of());
        return result;
    }
}
