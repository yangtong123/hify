package com.hify.modules.mcp.api;

import com.hify.common.web.PageResult;
import com.hify.modules.mcp.api.dto.McpConnectionTestResult;
import com.hify.modules.mcp.api.dto.McpServerDetailResponse;
import com.hify.modules.mcp.api.dto.McpServerDto;
import com.hify.modules.mcp.api.dto.McpServerQuery;
import com.hify.modules.mcp.api.dto.McpServerRequest;
import com.hify.modules.mcp.api.dto.McpServerResponse;
import com.hify.modules.mcp.api.dto.McpToolDto;

import java.util.List;

public interface McpService {

    McpServerResponse create(McpServerRequest request);

    McpServerResponse update(Long id, McpServerRequest request);

    void delete(Long id);

    McpServerDetailResponse getById(Long id);

    PageResult<List<McpServerResponse>> list(McpServerQuery query);

    McpConnectionTestResult testConnection(Long id);

    McpServerDto getServer(Long serverId);

    List<McpServerDto> listServers(Integer enabled);

    List<McpServerDto> listServersByIds(List<Long> serverIds);

    List<McpToolDto> listAgentTools(Long agentId);
}
