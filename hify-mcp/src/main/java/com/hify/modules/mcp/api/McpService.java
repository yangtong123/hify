package com.hify.modules.mcp.api;

import com.hify.modules.mcp.api.dto.McpServerDto;

import java.util.List;

public interface McpService {

    McpServerDto getServer(Long serverId);

    List<McpServerDto> listServers(Integer enabled);

    List<McpServerDto> listServersByIds(List<Long> serverIds);
}
