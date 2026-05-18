package com.hify.modules.mcp.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.modules.mcp.api.McpService;
import com.hify.modules.mcp.api.dto.McpConnectionTestResult;
import com.hify.modules.mcp.api.dto.McpServerDetailResponse;
import com.hify.modules.mcp.api.dto.McpServerDto;
import com.hify.modules.mcp.api.dto.McpServerQuery;
import com.hify.modules.mcp.api.dto.McpServerRequest;
import com.hify.modules.mcp.api.dto.McpServerResponse;
import com.hify.modules.mcp.api.dto.McpToolDto;
import com.hify.modules.mcp.api.dto.McpToolResponse;
import com.hify.modules.mcp.infra.client.McpClientFactory;
import com.hify.modules.mcp.infra.mapper.AgentMcpBindingMapper;
import com.hify.modules.mcp.infra.mapper.McpServerMapper;
import com.hify.modules.mcp.infra.mapper.McpToolMapper;
import com.hify.modules.mcp.infra.po.AgentMcpBindingPo;
import com.hify.modules.mcp.infra.po.McpServerPo;
import com.hify.modules.mcp.infra.po.McpToolPo;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpServiceImpl implements McpService {

    private final McpServerMapper mcpServerMapper;
    private final McpToolMapper mcpToolMapper;
    private final AgentMcpBindingMapper agentMcpBindingMapper;
    private final McpClientFactory mcpClientFactory;

    @Override
    @Transactional
    public McpServerResponse create(McpServerRequest request) {
        checkNameDuplicate(request.getName(), null);

        McpServerPo po = new McpServerPo();
        po.setName(request.getName());
        po.setServerType("streamable_http");
        po.setUrl(request.getEndpoint());
        po.setIsEnabled(defaultEnabled(request.getEnabled()));
        mcpServerMapper.insert(po);

        log.info("MCP server created: id={}, name={}, endpoint={}", po.getId(), po.getName(), po.getUrl());
        return toResponse(po, 0);
    }

    @Override
    @Transactional
    public McpServerResponse update(Long id, McpServerRequest request) {
        McpServerPo existing = mcpServerMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND);
        }
        checkNameDuplicate(request.getName(), id);

        McpServerPo po = new McpServerPo();
        po.setId(id);
        po.setName(request.getName());
        po.setServerType("streamable_http");
        po.setUrl(request.getEndpoint());
        po.setIsEnabled(defaultEnabled(request.getEnabled()));
        mcpServerMapper.updateById(po);

        log.info("MCP server updated: id={}, name={}, endpoint={}", id, request.getName(), request.getEndpoint());
        return toResponse(mcpServerMapper.selectById(id), countTools(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        McpServerPo existing = mcpServerMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND);
        }
        Long bindingCount = agentMcpBindingMapper.selectCount(new LambdaQueryWrapper<AgentMcpBindingPo>()
                .eq(AgentMcpBindingPo::getMcpServerId, id));
        if (bindingCount > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "已有 Agent 绑定该 MCP Server，不能删除");
        }

        mcpToolMapper.deletePhysicallyByServerId(id);
        mcpServerMapper.deleteById(id);
        log.info("MCP server deleted: id={}, name={}", id, existing.getName());
    }

    @Override
    public McpServerDetailResponse getById(Long id) {
        McpServerPo server = mcpServerMapper.selectById(id);
        if (server == null) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND);
        }
        List<McpToolPo> tools = listToolPos(id);
        McpServerDetailResponse response = new McpServerDetailResponse();
        copyToResponse(server, response);
        response.setToolCount(tools.size());
        response.setTools(tools.stream().map(this::toToolResponse).toList());
        return response;
    }

    @Override
    public PageResult<List<McpServerResponse>> list(McpServerQuery query) {
        LambdaQueryWrapper<McpServerPo> wrapper = new LambdaQueryWrapper<McpServerPo>()
                .like(StringUtils.hasText(query.getName()), McpServerPo::getName, query.getName())
                .eq(query.getEnabled() != null, McpServerPo::getIsEnabled, query.getEnabled())
                .orderByDesc(McpServerPo::getId);
        Page<McpServerPo> page = PageHelper.toPage(query.getPage(), query.getSize());
        Page<McpServerPo> result = mcpServerMapper.selectPage(page, wrapper);

        Map<Long, Integer> toolCountMap = countToolsByServer(result.getRecords().stream().map(McpServerPo::getId).toList());
        List<McpServerResponse> list = result.getRecords().stream()
                .map(po -> toResponse(po, toolCountMap.getOrDefault(po.getId(), 0)))
                .toList();
        return PageResult.ok(list, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    @Transactional
    public McpConnectionTestResult testConnection(Long id) {
        McpServerPo server = mcpServerMapper.selectById(id);
        if (server == null) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND);
        }

        long start = System.currentTimeMillis();
        try (var client = mcpClientFactory.create(server)) {
            client.initialize();
            McpSchema.ListToolsResult toolsResult = client.listTools();
            List<McpSchema.Tool> tools = toolsResult.tools();
            syncTools(id, tools);
            long latency = System.currentTimeMillis() - start;
            log.info("MCP server connection test completed: id={}, success=true, latency={}ms, tools={}",
                    id, latency, tools.size());
            return McpConnectionTestResult.success(latency, tools.stream().map(McpSchema.Tool::name).toList());
        } catch (Exception | LinkageError e) {
            long latency = System.currentTimeMillis() - start;
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("MCP server connection test failed: id={}, latency={}ms, errorType={}, error={}",
                    id, latency, e.getClass().getSimpleName(), message);
            return McpConnectionTestResult.fail(latency, message);
        }
    }

    @Override
    public McpServerDto getServer(Long serverId) {
        log.info("MCP server lookup started: serverId={}", serverId);
        McpServerPo server = mcpServerMapper.selectById(serverId);
        if (server == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "MCP Server 不存在");
        }
        log.info("MCP server lookup completed: serverId={}, name={}, enabled={}",
                server.getId(), server.getName(), server.getIsEnabled());
        return toDto(server);
    }

    @Override
    public List<McpServerDto> listServers(Integer enabled) {
        List<McpServerDto> servers = mcpServerMapper.selectList(new LambdaQueryWrapper<McpServerPo>()
                        .eq(enabled != null, McpServerPo::getIsEnabled, enabled)
                        .orderByDesc(McpServerPo::getId))
                .stream()
                .map(this::toDto)
                .toList();
        log.info("MCP servers listed: enabled={}, count={}", enabled, servers.size());
        return servers;
    }

    @Override
    public List<McpServerDto> listServersByIds(List<Long> serverIds) {
        if (serverIds == null || serverIds.isEmpty()) {
            log.info("MCP servers batch lookup skipped: reason=empty_ids");
            return List.of();
        }
        List<McpServerDto> servers = mcpServerMapper.selectBatchIds(serverIds).stream()
                .map(this::toDto)
                .toList();
        log.info("MCP servers batch lookup completed: requested={}, found={}", serverIds.size(), servers.size());
        return servers;
    }

    @Override
    public List<McpToolDto> listAgentTools(Long agentId) {
        if (agentId == null) {
            return List.of();
        }
        List<Long> serverIds = agentMcpBindingMapper.selectList(new LambdaQueryWrapper<AgentMcpBindingPo>()
                        .eq(AgentMcpBindingPo::getAgentId, agentId))
                .stream()
                .map(AgentMcpBindingPo::getMcpServerId)
                .distinct()
                .toList();
        if (serverIds.isEmpty()) {
            return List.of();
        }
        List<McpToolDto> tools = mcpToolMapper.selectList(new LambdaQueryWrapper<McpToolPo>()
                        .in(McpToolPo::getMcpServerId, serverIds)
                        .orderByAsc(McpToolPo::getMcpServerId)
                        .orderByAsc(McpToolPo::getName))
                .stream()
                .map(this::toToolDto)
                .toList();
        log.info("MCP agent tools listed: agentId={}, servers={}, tools={}", agentId, serverIds.size(), tools.size());
        return tools;
    }

    private void checkNameDuplicate(String name, Long excludeId) {
        LambdaQueryWrapper<McpServerPo> wrapper = new LambdaQueryWrapper<McpServerPo>()
                .eq(McpServerPo::getName, name);
        if (excludeId != null) {
            wrapper.ne(McpServerPo::getId, excludeId);
        }
        if (mcpServerMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "MCP Server 名称已存在: " + name);
        }
    }

    private void syncTools(Long serverId, List<McpSchema.Tool> tools) {
        mcpToolMapper.deletePhysicallyByServerId(serverId);
        for (McpSchema.Tool tool : tools) {
            McpToolPo po = new McpToolPo();
            po.setMcpServerId(serverId);
            po.setName(tool.name());
            po.setDescription(tool.description());
            po.setInputSchema(toSchemaMap(tool.inputSchema()));
            mcpToolMapper.insert(po);
        }
        log.info("MCP tools synced: serverId={}, tools={}", serverId, tools.size());
    }

    private Map<String, Object> toSchemaMap(McpSchema.JsonSchema inputSchema) {
        if (inputSchema == null) {
            return Map.of();
        }
        Map<String, Object> schema = new HashMap<>();
        putIfNotNull(schema, "type", inputSchema.type());
        putIfNotNull(schema, "properties", inputSchema.properties());
        putIfNotNull(schema, "required", inputSchema.required());
        putIfNotNull(schema, "additionalProperties", inputSchema.additionalProperties());
        putIfNotNull(schema, "$defs", inputSchema.defs());
        putIfNotNull(schema, "definitions", inputSchema.definitions());
        return schema;
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private List<McpToolPo> listToolPos(Long serverId) {
        return mcpToolMapper.selectList(new LambdaQueryWrapper<McpToolPo>()
                .eq(McpToolPo::getMcpServerId, serverId)
                .orderByAsc(McpToolPo::getName));
    }

    private int countTools(Long serverId) {
        return Math.toIntExact(mcpToolMapper.selectCount(new LambdaQueryWrapper<McpToolPo>()
                .eq(McpToolPo::getMcpServerId, serverId)));
    }

    private Map<Long, Integer> countToolsByServer(List<Long> serverIds) {
        if (serverIds == null || serverIds.isEmpty()) {
            return Map.of();
        }
        List<McpToolPo> tools = mcpToolMapper.selectList(new LambdaQueryWrapper<McpToolPo>()
                .in(McpToolPo::getMcpServerId, serverIds));
        Map<Long, Integer> countMap = new HashMap<>();
        for (McpToolPo tool : tools) {
            countMap.merge(tool.getMcpServerId(), 1, Integer::sum);
        }
        return countMap;
    }

    private McpServerResponse toResponse(McpServerPo po, int toolCount) {
        McpServerResponse response = new McpServerResponse();
        copyToResponse(po, response);
        response.setToolCount(toolCount);
        return response;
    }

    private void copyToResponse(McpServerPo po, McpServerResponse response) {
        response.setId(po.getId());
        response.setName(po.getName());
        response.setEndpoint(po.getUrl());
        response.setEnabled(po.getIsEnabled());
        response.setCreatedAt(po.getCreatedAt());
        response.setUpdatedAt(po.getUpdatedAt());
    }

    private McpToolResponse toToolResponse(McpToolPo po) {
        McpToolResponse response = new McpToolResponse();
        response.setId(po.getId());
        response.setMcpServerId(po.getMcpServerId());
        response.setName(po.getName());
        response.setDescription(po.getDescription());
        response.setInputSchema(po.getInputSchema());
        response.setCreatedAt(po.getCreatedAt());
        response.setUpdatedAt(po.getUpdatedAt());
        return response;
    }

    private McpToolDto toToolDto(McpToolPo po) {
        McpToolDto dto = new McpToolDto();
        dto.setId(po.getId());
        dto.setMcpServerId(po.getMcpServerId());
        dto.setName(po.getName());
        dto.setDescription(po.getDescription());
        dto.setInputSchema(po.getInputSchema());
        return dto;
    }

    private McpServerDto toDto(McpServerPo po) {
        McpServerDto dto = new McpServerDto();
        dto.setId(po.getId());
        dto.setName(po.getName());
        dto.setDescription(po.getDescription());
        dto.setServerType(po.getServerType());
        dto.setEnabled(po.getIsEnabled());
        return dto;
    }

    private Integer defaultEnabled(Integer enabled) {
        return Objects.equals(enabled, 0) ? 0 : 1;
    }
}
