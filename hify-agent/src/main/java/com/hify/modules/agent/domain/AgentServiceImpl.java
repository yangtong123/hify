package com.hify.modules.agent.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.modules.agent.api.AgentService;
import com.hify.modules.agent.api.dto.AgentConfigDto;
import com.hify.modules.agent.api.dto.AgentCreateRequest;
import com.hify.modules.agent.api.dto.AgentDetailResponse;
import com.hify.modules.agent.api.dto.AgentListResponse;
import com.hify.modules.agent.api.dto.AgentQuery;
import com.hify.modules.agent.api.dto.AgentToolsRequest;
import com.hify.modules.agent.api.dto.AgentUpdateRequest;
import com.hify.modules.agent.infra.mapper.AgentMapper;
import com.hify.modules.agent.infra.mapper.AgentMcpMapper;
import com.hify.modules.agent.infra.po.AgentConfig;
import com.hify.modules.agent.infra.po.AgentMcpPo;
import com.hify.modules.agent.infra.po.AgentPo;
import com.hify.modules.mcp.api.McpService;
import com.hify.modules.mcp.api.dto.McpServerDto;
import com.hify.modules.knowledge.api.KnowledgeService;
import com.hify.modules.knowledge.api.dto.AgentKnowledgeBaseRequest;
import com.hify.modules.knowledge.api.dto.KnowledgeBaseResponse;
import com.hify.modules.provider.api.ProviderService;
import com.hify.modules.provider.api.dto.ModelConfigDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private static final BigDecimal DEFAULT_TEMPERATURE = new BigDecimal("0.70");
    private static final BigDecimal DEFAULT_TOP_P = new BigDecimal("1.00");
    private static final int DEFAULT_MAX_TOKENS = 4096;
    private static final int DEFAULT_MAX_CONTEXT_TURNS = 10;

    private final AgentMapper agentMapper;
    private final AgentMcpMapper agentMcpMapper;
    private final ProviderService providerService;
    private final McpService mcpService;
    private final KnowledgeService knowledgeService;

    @Override
    @Transactional
    public AgentDetailResponse create(AgentCreateRequest request) {
        checkNameDuplicate(request.getName(), null);
        validateModelEnabled(request.getModelConfigId());
        List<Long> mcpServerIds = validateMcpServers(request.getMcpServerIds());

        AgentPo po = toPo(request);
        agentMapper.insert(po);
        replaceTools(po.getId(), mcpServerIds);
        replaceKnowledgeBases(po.getId(), request.getKnowledgeBaseIds());

        return buildDetail(po);
    }

    @Override
    @Transactional
    public AgentDetailResponse update(Long id, AgentUpdateRequest request) {
        AgentPo po = agentMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent 不存在");
        }

        checkNameDuplicate(request.getName(), id);
        validateModelEnabled(request.getModelConfigId());
        List<Long> mcpServerIds = validateMcpServers(request.getMcpServerIds());

        po.setName(request.getName());
        po.setDescription(request.getDescription());
        po.setSystemPrompt(request.getSystemPrompt());
        po.setModelConfigId(request.getModelConfigId());
        po.setTemperature(defaultIfNull(request.getTemperature(), DEFAULT_TEMPERATURE));
        po.setMaxTokens(defaultIfNull(request.getMaxTokens(), DEFAULT_MAX_TOKENS));
        po.setTopP(defaultIfNull(request.getTopP(), DEFAULT_TOP_P));
        po.setMaxContextTurns(defaultIfNull(request.getMaxContextTurns(), DEFAULT_MAX_CONTEXT_TURNS));
        po.setConfigJson(toConfig(request.getConfigJson()));
        po.setEnabled(defaultIfNull(request.getEnabled(), 1));
        po.setUpdatedAt(null);

        agentMapper.updateById(po);
        replaceTools(id, mcpServerIds);
        replaceKnowledgeBases(id, request.getKnowledgeBaseIds());

        return buildDetail(po);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AgentPo po = agentMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent 不存在");
        }
        agentMapper.deleteById(id);
        deleteTools(id);
        replaceKnowledgeBases(id, List.of());
    }

    @Override
    public AgentDetailResponse getById(Long id) {
        AgentPo po = agentMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent 不存在");
        }
        return buildDetail(po);
    }

    @Override
    public PageResult<List<AgentListResponse>> list(AgentQuery query) {
        LambdaQueryWrapper<AgentPo> wrapper = new LambdaQueryWrapper<AgentPo>()
                .like(StringUtils.hasText(query.getName()), AgentPo::getName, query.getName())
                .eq(query.getEnabled() != null, AgentPo::getEnabled, query.getEnabled())
                .orderByDesc(AgentPo::getCreatedAt);

        Page<AgentPo> page = PageHelper.toPage(query.getPage(), query.getSize());
        IPage<AgentPo> result = agentMapper.selectPage(page, wrapper);

        List<AgentListResponse> list = enrichList(result.getRecords());
        return PageResult.ok(list, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    @Transactional
    public void updateTools(Long agentId, AgentToolsRequest request) {
        AgentPo po = agentMapper.selectById(agentId);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent 不存在");
        }
        replaceTools(agentId, validateMcpServers(request.getMcpServerIds()));
    }

    // ---- private helpers ----

    private void checkNameDuplicate(String name, Long excludeId) {
        LambdaQueryWrapper<AgentPo> wrapper = new LambdaQueryWrapper<AgentPo>()
                .eq(AgentPo::getName, name);
        if (excludeId != null) {
            wrapper.ne(AgentPo::getId, excludeId);
        }
        if (agentMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "Agent 名称已存在: " + name);
        }
    }

    private AgentPo toPo(AgentCreateRequest request) {
        AgentPo po = new AgentPo();
        po.setName(request.getName());
        po.setDescription(request.getDescription());
        po.setSystemPrompt(request.getSystemPrompt());
        po.setModelConfigId(request.getModelConfigId());
        po.setTemperature(defaultIfNull(request.getTemperature(), DEFAULT_TEMPERATURE));
        po.setMaxTokens(defaultIfNull(request.getMaxTokens(), DEFAULT_MAX_TOKENS));
        po.setTopP(defaultIfNull(request.getTopP(), DEFAULT_TOP_P));
        po.setMaxContextTurns(defaultIfNull(request.getMaxContextTurns(), DEFAULT_MAX_CONTEXT_TURNS));
        po.setConfigJson(toConfig(request.getConfigJson()));
        po.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        return po;
    }

    private List<AgentListResponse> enrichList(List<AgentPo> agents) {
        if (agents.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> agentIds = agents.stream().map(AgentPo::getId).toList();
        List<Long> modelIds = agents.stream().map(AgentPo::getModelConfigId).filter(Objects::nonNull).distinct().toList();

        Map<Long, ModelConfigDto> modelMap = Collections.emptyMap();
        if (!modelIds.isEmpty()) {
            modelMap = providerService.listModelConfigsByIds(modelIds).stream()
                    .collect(Collectors.toMap(ModelConfigDto::getId, Function.identity()));
        }

        // count MCP servers per agent
        List<AgentMcpPo> allMappings = agentMcpMapper.selectList(
                new LambdaQueryWrapper<AgentMcpPo>().in(AgentMcpPo::getAgentId, agentIds));
        Map<Long, Long> mcpCountMap = allMappings.stream()
                .collect(Collectors.groupingBy(AgentMcpPo::getAgentId, Collectors.counting()));

        List<AgentListResponse> list = new ArrayList<>();
        for (AgentPo agent : agents) {
            AgentListResponse resp = toListResponse(agent);
            ModelConfigDto model = modelMap.get(agent.getModelConfigId());
            if (model != null) {
                resp.setModelName(model.getName());
                resp.setProviderName(model.getProviderName());
            }
            resp.setMcpServerCount(mcpCountMap.getOrDefault(agent.getId(), 0L).intValue());
            resp.setKnowledgeBaseCount(knowledgeService.listAgentKnowledgeBases(agent.getId()).size());
            list.add(resp);
        }
        return list;
    }

    private AgentListResponse toListResponse(AgentPo po) {
        AgentListResponse resp = new AgentListResponse();
        resp.setId(po.getId());
        resp.setName(po.getName());
        resp.setDescription(po.getDescription());
        resp.setSystemPrompt(po.getSystemPrompt());
        resp.setModelConfigId(po.getModelConfigId());
        resp.setTemperature(po.getTemperature());
        resp.setMaxTokens(po.getMaxTokens());
        resp.setTopP(po.getTopP());
        resp.setMaxContextTurns(po.getMaxContextTurns());
        resp.setConfigJson(toConfigDto(po.getConfigJson()));
        resp.setEnabled(po.getEnabled());
        resp.setCreatedAt(po.getCreatedAt());
        resp.setUpdatedAt(po.getUpdatedAt());
        return resp;
    }

    private AgentDetailResponse buildDetail(AgentPo po) {
        AgentDetailResponse resp = new AgentDetailResponse();
        // base fields
        resp.setId(po.getId());
        resp.setName(po.getName());
        resp.setDescription(po.getDescription());
        resp.setSystemPrompt(po.getSystemPrompt());
        resp.setModelConfigId(po.getModelConfigId());
        resp.setTemperature(po.getTemperature());
        resp.setMaxTokens(po.getMaxTokens());
        resp.setTopP(po.getTopP());
        resp.setMaxContextTurns(po.getMaxContextTurns());
        resp.setConfigJson(toConfigDto(po.getConfigJson()));
        resp.setEnabled(po.getEnabled());
        resp.setCreatedAt(po.getCreatedAt());
        resp.setUpdatedAt(po.getUpdatedAt());

        ModelConfigDto model = providerService.getModelConfig(po.getModelConfigId());
        if (model != null) {
            resp.setModelName(model.getName());
            resp.setProviderName(model.getProviderName());
            AgentDetailResponse.ModelInfo modelInfo = new AgentDetailResponse.ModelInfo();
            modelInfo.setId(model.getId());
            modelInfo.setName(model.getName());
            modelInfo.setContextSize(model.getContextSize());
            modelInfo.setProviderName(model.getProviderName());
            resp.setModel(modelInfo);
        }

        List<AgentMcpPo> mappings = agentMcpMapper.selectList(
                new LambdaQueryWrapper<AgentMcpPo>().eq(AgentMcpPo::getAgentId, po.getId()));
        List<Long> mcpServerIds = mappings.stream().map(AgentMcpPo::getMcpServerId).toList();
        resp.setMcpServerCount(mcpServerIds.size());

        if (!mcpServerIds.isEmpty()) {
            List<McpServerDto> servers = mcpService.listServersByIds(mcpServerIds);
            List<AgentDetailResponse.McpServerInfo> serverInfos = servers.stream().map(s -> {
                AgentDetailResponse.McpServerInfo info = new AgentDetailResponse.McpServerInfo();
                info.setId(s.getId());
                info.setName(s.getName());
                info.setServerType(s.getServerType());
                info.setIsEnabled(Objects.equals(s.getEnabled(), 1));
                return info;
            }).toList();
            resp.setMcpServers(serverInfos);
        } else {
            resp.setMcpServers(Collections.emptyList());
        }

        List<KnowledgeBaseResponse> knowledgeBases = knowledgeService.listAgentKnowledgeBases(po.getId());
        resp.setKnowledgeBaseCount(knowledgeBases.size());
        resp.setKnowledgeBases(knowledgeBases.stream().map(item -> {
            AgentDetailResponse.KnowledgeBaseInfo info = new AgentDetailResponse.KnowledgeBaseInfo();
            info.setId(item.getId());
            info.setName(item.getName());
            info.setStatus(item.getStatus());
            return info;
        }).toList());

        return resp;
    }

    private void validateModelEnabled(Long modelConfigId) {
        ModelConfigDto model = providerService.getModelConfig(modelConfigId);
        if (!Objects.equals(model.getEnabled(), 1)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "模型配置未启用: " + modelConfigId);
        }
    }

    private List<Long> validateMcpServers(List<Long> mcpServerIds) {
        if (mcpServerIds == null || mcpServerIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> distinctIds = new LinkedHashSet<>();
        for (Long mcpServerId : mcpServerIds) {
            if (mcpServerId == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "MCP Server ID 不能为空");
            }
            distinctIds.add(mcpServerId);
        }

        List<McpServerDto> servers = mcpService.listServersByIds(new ArrayList<>(distinctIds));
        Map<Long, McpServerDto> serverMap = servers.stream()
                .collect(Collectors.toMap(McpServerDto::getId, Function.identity()));
        for (Long mcpServerId : distinctIds) {
            McpServerDto server = serverMap.get(mcpServerId);
            if (server == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "MCP Server 不存在: " + mcpServerId);
            }
            if (!Objects.equals(server.getEnabled(), 1)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "MCP Server 未启用: " + mcpServerId);
            }
        }
        return new ArrayList<>(distinctIds);
    }

    private void replaceTools(Long agentId, List<Long> mcpServerIds) {
        deleteTools(agentId);
        for (Long mcpServerId : mcpServerIds) {
            AgentMcpPo mapping = new AgentMcpPo();
            mapping.setAgentId(agentId);
            mapping.setMcpServerId(mcpServerId);
            agentMcpMapper.insert(mapping);
        }
    }

    private void deleteTools(Long agentId) {
        agentMcpMapper.delete(new LambdaQueryWrapper<AgentMcpPo>()
                .eq(AgentMcpPo::getAgentId, agentId));
    }

    private void replaceKnowledgeBases(Long agentId, List<Long> knowledgeBaseIds) {
        AgentKnowledgeBaseRequest request = new AgentKnowledgeBaseRequest();
        request.setKnowledgeBaseIds(knowledgeBaseIds != null ? knowledgeBaseIds : List.of());
        knowledgeService.updateAgentKnowledgeBases(agentId, request);
    }

    private <T> T defaultIfNull(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

    private AgentConfig toConfig(AgentConfigDto dto) {
        if (dto == null) {
            return null;
        }
        AgentConfig config = new AgentConfig();
        config.setOpeningMessage(dto.getOpeningMessage());
        config.setSuggestedQuestions(dto.getSuggestedQuestions());
        config.setMaxIterations(dto.getMaxIterations());
        return config;
    }

    private AgentConfigDto toConfigDto(AgentConfig config) {
        if (config == null) {
            return null;
        }
        AgentConfigDto dto = new AgentConfigDto();
        dto.setOpeningMessage(config.getOpeningMessage());
        dto.setSuggestedQuestions(config.getSuggestedQuestions());
        dto.setMaxIterations(config.getMaxIterations());
        return dto;
    }
}
