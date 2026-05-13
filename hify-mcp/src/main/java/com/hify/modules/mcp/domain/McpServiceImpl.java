package com.hify.modules.mcp.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.mcp.api.McpService;
import com.hify.modules.mcp.api.dto.McpServerDto;
import com.hify.modules.mcp.infra.mapper.McpServerMapper;
import com.hify.modules.mcp.infra.po.McpServerPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class McpServiceImpl implements McpService {

    private final McpServerMapper mcpServerMapper;

    @Override
    public McpServerDto getServer(Long serverId) {
        McpServerPo server = mcpServerMapper.selectById(serverId);
        if (server == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "MCP Server 不存在");
        }
        return toDto(server);
    }

    @Override
    public List<McpServerDto> listServers(Integer enabled) {
        return mcpServerMapper.selectList(new LambdaQueryWrapper<McpServerPo>()
                        .eq(enabled != null, McpServerPo::getIsEnabled, enabled)
                        .orderByDesc(McpServerPo::getId))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<McpServerDto> listServersByIds(List<Long> serverIds) {
        if (serverIds == null || serverIds.isEmpty()) {
            return List.of();
        }
        return mcpServerMapper.selectBatchIds(serverIds).stream()
                .map(this::toDto)
                .toList();
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
