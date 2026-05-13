package com.hify.modules.agent.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AgentToolsRequest {

    @NotNull(message = "MCP Server ID 列表不能为 null")
    private List<Long> mcpServerIds;
}
