package com.hify.modules.mcp.domain;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.mcp.api.McpClientService;
import com.hify.modules.mcp.infra.client.McpClientFactory;
import com.hify.modules.mcp.infra.mapper.McpServerMapper;
import com.hify.modules.mcp.infra.po.McpServerPo;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpClientServiceImpl implements McpClientService {

    private final McpServerMapper mcpServerMapper;
    private final McpClientFactory mcpClientFactory;

    @Override
    public String callTool(Long mcpServerId, String toolName, Map<String, Object> arguments) {
        McpServerPo server = getEnabledServer(mcpServerId);
        long start = System.currentTimeMillis();
        log.info("MCP tool call started: serverId={}, serverName={}, toolName={}, arguments={}",
                mcpServerId, server.getName(), toolName, arguments != null ? arguments.size() : 0);
        try (var client = mcpClientFactory.create(server)) {
            client.initialize();
            McpSchema.CallToolResult result = client.callTool(McpSchema.CallToolRequest.builder()
                    .name(toolName)
                    .arguments(arguments != null ? arguments : Map.of())
                    .build());
            String text = result.content().stream()
                    .filter(McpSchema.TextContent.class::isInstance)
                    .map(McpSchema.TextContent.class::cast)
                    .map(McpSchema.TextContent::text)
                    .collect(Collectors.joining("\n"));
            log.info("MCP tool call completed: serverId={}, toolName={}, latency={}ms, resultLength={}",
                    mcpServerId, toolName, System.currentTimeMillis() - start, text.length());
            return text;
        } catch (RuntimeException e) {
            log.warn("MCP tool call failed: serverId={}, toolName={}, latency={}ms, error={}",
                    mcpServerId, toolName, System.currentTimeMillis() - start, e.getMessage());
            throw new BizException(ErrorCode.MCP_TOOL_CALL_FAILED, "MCP 工具调用失败: " + e.getMessage());
        }
    }

    @Override
    public List<String> listTools(Long mcpServerId) {
        McpServerPo server = getEnabledServer(mcpServerId);
        try (var client = mcpClientFactory.create(server)) {
            client.initialize();
            return client.listTools().tools().stream()
                    .map(McpSchema.Tool::name)
                    .toList();
        } catch (RuntimeException e) {
            log.warn("MCP tools list failed: serverId={}, error={}", mcpServerId, e.getMessage());
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND, "MCP Server 不可用: " + e.getMessage());
        }
    }

    private McpServerPo getEnabledServer(Long mcpServerId) {
        McpServerPo server = mcpServerMapper.selectById(mcpServerId);
        if (server == null || !Integer.valueOf(1).equals(server.getIsEnabled())) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND);
        }
        return server;
    }
}
