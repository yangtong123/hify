package com.hify.modules.mcp.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.mcp.api.McpService;
import com.hify.modules.mcp.api.dto.McpServerDto;
import com.hify.modules.mcp.infra.mapper.McpServerMapper;
import com.hify.modules.mcp.infra.po.McpServerPo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpServiceImpl implements McpService {

    private final McpServerMapper mcpServerMapper;

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

    private McpServerDto toDto(McpServerPo po) {
        McpServerDto dto = new McpServerDto();
        dto.setId(po.getId());
        dto.setName(po.getName());
        dto.setDescription(po.getDescription());
        dto.setServerType(po.getServerType());
        dto.setEnabled(po.getIsEnabled());
        return dto;
    }
}
