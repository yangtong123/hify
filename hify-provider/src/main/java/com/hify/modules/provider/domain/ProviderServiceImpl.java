package com.hify.modules.provider.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.PageResult;
import com.hify.modules.provider.api.dto.ChatRequest;
import com.hify.modules.provider.api.dto.ChatResponse;
import com.hify.modules.provider.api.ProviderService;
import com.hify.modules.provider.api.dto.ConnectionTestResult;
import com.hify.modules.provider.api.dto.ModelConfigDto;
import com.hify.modules.provider.api.dto.ProviderDetailResponse;
import com.hify.modules.provider.api.dto.ProviderDetailResponse.HealthSummary;
import com.hify.modules.provider.api.dto.ProviderDetailResponse.ModelConfigResponse;
import com.hify.modules.provider.api.dto.ProviderQuery;
import com.hify.modules.provider.api.dto.ProviderRequest;
import com.hify.modules.provider.api.dto.ProviderResponse;
import com.hify.modules.provider.infra.adapter.ProviderAdapterFactory;
import com.hify.modules.provider.infra.mapper.ModelConfigMapper;
import com.hify.modules.provider.infra.mapper.ProviderHealthCheckMapper;
import com.hify.modules.provider.infra.mapper.ProviderMapper;
import com.hify.modules.provider.infra.po.AuthConfig;
import com.hify.modules.provider.infra.po.ModelConfigPo;
import com.hify.modules.provider.infra.po.ProviderHealthCheckPo;
import com.hify.modules.provider.infra.po.ProviderPo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderServiceImpl implements ProviderService {

    private final ProviderMapper providerMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final ProviderHealthCheckMapper healthCheckMapper;
    private final ProviderAdapterFactory adapterFactory;

    @Override
    @Transactional
    @CacheEvict(cacheNames = "provider-cache", allEntries = true)
    public ProviderResponse create(ProviderRequest request) {
        checkNameDuplicate(request.getName(), null);

        ProviderPo po = new ProviderPo();
        po.setName(request.getName());
        po.setType(request.getType());
        po.setBaseUrl(request.getBaseUrl());
        po.setEnabled(request.getEnabled());
        po.setAuthConfig(request.getAuthConfig());
        providerMapper.insert(po);

        log.info("Provider created: id={}, name={}, type={}", po.getId(), po.getName(), po.getType());
        return toResponse(po);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "provider-cache", allEntries = true)
    public ProviderResponse update(Long id, ProviderRequest request) {
        ProviderPo existing = providerMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "提供商不存在");
        }
        checkNameDuplicate(request.getName(), id);

        ProviderPo po = new ProviderPo();
        po.setId(id);
        po.setName(request.getName());
        po.setType(request.getType());
        po.setBaseUrl(request.getBaseUrl());
        po.setEnabled(request.getEnabled());
        po.setAuthConfig(request.getAuthConfig());
        providerMapper.updateById(po);

        log.info("Provider updated: id={}", id);
        return toResponse(providerMapper.selectById(id));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "provider-cache", allEntries = true)
    public void delete(Long id) {
        int rows = providerMapper.deleteById(id);
        if (rows == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "提供商不存在");
        }
        log.info("Provider deleted: id={}", id);
    }

    @Override
    @Cacheable(cacheNames = "provider-cache", key = "'detail:' + #id")
    public ProviderDetailResponse getById(Long id) {
        ProviderPo provider = providerMapper.selectById(id);
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "提供商不存在");
        }

        ProviderDetailResponse detail = new ProviderDetailResponse();
        copyToResponse(provider, detail);

        List<ModelConfigPo> models = modelConfigMapper.selectList(
                new LambdaQueryWrapper<ModelConfigPo>()
                        .eq(ModelConfigPo::getProviderId, id)
                        .orderByAsc(ModelConfigPo::getId));
        detail.setModels(models.stream().map(this::toModelConfigResponse).toList());

        detail.setHealth(computeHealth(id));

        return detail;
    }

    @Override
    @Cacheable(cacheNames = "provider-cache",
            key = "'list:type=' + #query.type + ':enabled=' + #query.enabled + ':page=' + #query.page + ':size=' + #query.size")
    public PageResult<List<ProviderResponse>> list(ProviderQuery query) {
        LambdaQueryWrapper<ProviderPo> wrapper = new LambdaQueryWrapper<>();
        if (query.getType() != null && !query.getType().isBlank()) {
            wrapper.eq(ProviderPo::getType, query.getType());
        }
        if (query.getEnabled() != null) {
            wrapper.eq(ProviderPo::getEnabled, query.getEnabled());
        }
        wrapper.orderByDesc(ProviderPo::getId);

        Page<ProviderPo> page = new Page<>(query.getPage(), query.getSize());
        Page<ProviderPo> result = providerMapper.selectPage(page, wrapper);

        List<ProviderResponse> list = result.getRecords().stream()
                .map(this::toResponse)
                .toList();

        if (!list.isEmpty()) {
            List<Long> providerIds = list.stream().map(ProviderResponse::getId).toList();
            enrichWithHealth(list, providerIds);
            enrichWithModelCount(list, providerIds);
        }

        return PageResult.ok(list, result.getTotal(), query.getPage(), query.getSize());
    }

    @Override
    public List<ModelConfigDto> listAvailableModelConfigs() {
        List<ModelConfigPo> models = modelConfigMapper.selectList(
                new LambdaQueryWrapper<ModelConfigPo>()
                        .eq(ModelConfigPo::getEnabled, 1)
                        .orderByAsc(ModelConfigPo::getProviderId)
                        .orderByAsc(ModelConfigPo::getId));
        if (models.isEmpty()) {
            return List.of();
        }

        List<Long> providerIds = models.stream()
                .map(ModelConfigPo::getProviderId)
                .distinct()
                .toList();
        Map<Long, ProviderPo> providerMap = new HashMap<>();
        for (ProviderPo provider : providerMapper.selectBatchIds(providerIds)) {
            if (Objects.equals(provider.getEnabled(), 1)) {
                providerMap.put(provider.getId(), provider);
            }
        }

        return models.stream()
                .filter(model -> providerMap.containsKey(model.getProviderId()))
                .map(model -> toModelConfigDto(model, providerMap.get(model.getProviderId())))
                .toList();
    }

    private void enrichWithHealth(List<ProviderResponse> list, List<Long> providerIds) {
        List<ProviderHealthCheckPo> healthRecords = healthCheckMapper.selectList(
                new LambdaQueryWrapper<ProviderHealthCheckPo>()
                        .in(ProviderHealthCheckPo::getProviderId, providerIds));
        Map<Long, ProviderHealthCheckPo> healthMap = new HashMap<>();
        for (ProviderHealthCheckPo h : healthRecords) {
            healthMap.put(h.getProviderId(), h);
        }
        for (ProviderResponse resp : list) {
            ProviderHealthCheckPo h = healthMap.get(resp.getId());
            if (h != null) {
                resp.setHealthStatus(h.getStatus() != null ? h.getStatus().toUpperCase() : "UNKNOWN");
                resp.setHealthLatencyMs(h.getLatencyMs());
            } else {
                resp.setHealthStatus("UNKNOWN");
            }
        }
    }

    private void enrichWithModelCount(List<ProviderResponse> list, List<Long> providerIds) {
        List<ModelConfigPo> models = modelConfigMapper.selectList(
                new LambdaQueryWrapper<ModelConfigPo>()
                        .in(ModelConfigPo::getProviderId, providerIds)
                        .eq(ModelConfigPo::getEnabled, 1));
        Map<Long, Long> countMap = new HashMap<>();
        for (ModelConfigPo m : models) {
            countMap.merge(m.getProviderId(), 1L, Long::sum);
        }
        for (ProviderResponse resp : list) {
            resp.setModelCount(countMap.getOrDefault(resp.getId(), 0L).intValue());
        }
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "provider-cache", allEntries = true)
    public ConnectionTestResult testConnection(Long id) {
        ProviderPo provider = providerMapper.selectById(id);
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "提供商不存在");
        }

        var adapter = adapterFactory.getAdapter(provider.getType());
        ConnectionTestResult result = adapter.testConnection(provider);
        if (result.isSuccess()) {
            List<String> modelIds = adapter.listModels(provider);
            if (modelIds != null) {
                syncModelConfigs(provider.getId(), modelIds);
                result.setModelCount(modelIds.size());
            }
        }
        return result;
    }

    @Override
    public ModelConfigDto getModelConfig(Long modelConfigId) {
        ModelConfigPo model = modelConfigMapper.selectById(modelConfigId);
        if (model == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型配置不存在");
        }
        ProviderPo provider = providerMapper.selectById(model.getProviderId());
        return toModelConfigDto(model, provider);
    }

    @Override
    public List<ModelConfigDto> listModelConfigsByIds(List<Long> modelConfigIds) {
        if (modelConfigIds == null || modelConfigIds.isEmpty()) {
            return List.of();
        }
        List<ModelConfigPo> models = modelConfigMapper.selectBatchIds(modelConfigIds);
        List<Long> providerIds = models.stream()
                .map(ModelConfigPo::getProviderId)
                .distinct()
                .toList();
        Map<Long, ProviderPo> providerMap = new HashMap<>();
        if (!providerIds.isEmpty()) {
            for (ProviderPo provider : providerMapper.selectBatchIds(providerIds)) {
                providerMap.put(provider.getId(), provider);
            }
        }
        return models.stream()
                .map(model -> toModelConfigDto(model, providerMap.get(model.getProviderId())))
                .toList();
    }

    @Override
    public ChatResponse chat(Long modelConfigId, ChatRequest request) {
        ChatInvocation invocation = buildChatInvocation(modelConfigId, request);
        return adapterFactory.getAdapter(invocation.provider().getType())
                .chat(invocation.provider(), invocation.request());
    }

    @Override
    public void streamChat(Long modelConfigId, ChatRequest request, Consumer<ChatResponse> chunkConsumer) {
        ChatInvocation invocation = buildChatInvocation(modelConfigId, request);
        adapterFactory.getAdapter(invocation.provider().getType())
                .streamChat(invocation.provider(), invocation.request(), chunkConsumer);
    }

    private void checkNameDuplicate(String name, Long excludeId) {
        LambdaQueryWrapper<ProviderPo> wrapper = new LambdaQueryWrapper<ProviderPo>()
                .eq(ProviderPo::getName, name);
        if (excludeId != null) {
            wrapper.ne(ProviderPo::getId, excludeId);
        }
        if (providerMapper.exists(wrapper)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "提供商名称已存在: " + name);
        }
    }

    private void syncModelConfigs(Long providerId, List<String> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return;
        }

        List<ModelConfigPo> existingModels = modelConfigMapper.selectList(
                new LambdaQueryWrapper<ModelConfigPo>()
                        .eq(ModelConfigPo::getProviderId, providerId));
        if (existingModels == null) {
            existingModels = List.of();
        }
        Map<String, ModelConfigPo> existingByModelId = new HashMap<>();
        for (ModelConfigPo model : existingModels) {
            existingByModelId.put(model.getModelId(), model);
        }

        for (String modelId : modelIds.stream().filter(this::hasText).distinct().toList()) {
            if (existingByModelId.containsKey(modelId)) {
                continue;
            }

            ModelConfigPo model = new ModelConfigPo();
            model.setProviderId(providerId);
            model.setName(modelId);
            model.setModelId(modelId);
            model.setContextSize(inferContextSize(modelId));
            model.setEnabled(1);
            modelConfigMapper.insert(model);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Integer inferContextSize(String modelId) {
        if (modelId == null) {
            return null;
        }
        String normalized = modelId.toLowerCase();
        if (normalized.contains("128k") || normalized.contains("gpt-4o") || normalized.contains("gpt-4.1")) {
            return 128000;
        }
        if (normalized.contains("32k")) {
            return 32000;
        }
        if (normalized.contains("16k")) {
            return 16000;
        }
        if (normalized.contains("8k")) {
            return 8000;
        }
        return null;
    }

    private HealthSummary computeHealth(Long providerId) {
        ProviderHealthCheckPo record = healthCheckMapper.selectOne(
                new LambdaQueryWrapper<ProviderHealthCheckPo>()
                        .eq(ProviderHealthCheckPo::getProviderId, providerId));

        if (record == null) {
            HealthSummary summary = new HealthSummary();
            summary.setStatus("unknown");
            return summary;
        }

        HealthSummary summary = new HealthSummary();
        summary.setStatus(record.getStatus() != null ? record.getStatus().toLowerCase() : "unknown");
        summary.setLastCheckAt(record.getLastCheckAt());
        summary.setLastSuccessAt(record.getLastSuccessAt());
        summary.setLatencyMs(record.getLatencyMs());
        summary.setFailCount(record.getFailCount());
        return summary;
    }

    private ProviderResponse toResponse(ProviderPo po) {
        ProviderResponse resp = new ProviderResponse();
        copyToResponse(po, resp);
        return resp;
    }

    private void copyToResponse(ProviderPo po, ProviderResponse resp) {
        resp.setId(po.getId());
        resp.setName(po.getName());
        resp.setType(po.getType());
        resp.setBaseUrl(po.getBaseUrl());
        resp.setEnabled(po.getEnabled());
        resp.setAuthConfig(maskAuthConfig(po.getAuthConfig()));
        resp.setApiKey(extractMaskedApiKey(po.getAuthConfig()));
        resp.setCreatedAt(po.getCreatedAt());
        resp.setUpdatedAt(po.getUpdatedAt());
    }

    private AuthConfig maskAuthConfig(AuthConfig source) {
        if (source == null) {
            return null;
        }
        AuthConfig masked = new AuthConfig();
        masked.setAuthType(source.getAuthType());
        masked.setApiKey(extractMaskedApiKey(source));
        masked.setApiVersion(source.getApiVersion());
        masked.setCustomHeaders(source.getCustomHeaders());
        return masked;
    }

    private String extractMaskedApiKey(AuthConfig authConfig) {
        if (authConfig == null) {
            return null;
        }
        String key = authConfig.getApiKey();
        if (key == null || key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    private ModelConfigResponse toModelConfigResponse(ModelConfigPo po) {
        ModelConfigResponse resp = new ModelConfigResponse();
        resp.setId(po.getId());
        resp.setName(po.getName());
        resp.setModelId(po.getModelId());
        resp.setContextSize(po.getContextSize());
        resp.setEnabled(po.getEnabled());
        resp.setExtraParams(po.getExtraParams());
        return resp;
    }

    private ModelConfigDto toModelConfigDto(ModelConfigPo model, ProviderPo provider) {
        ModelConfigDto dto = new ModelConfigDto();
        dto.setId(model.getId());
        dto.setProviderId(model.getProviderId());
        dto.setName(model.getName());
        dto.setModelId(model.getModelId());
        dto.setContextSize(model.getContextSize());
        dto.setEnabled(model.getEnabled());
        if (provider != null) {
            dto.setProviderName(provider.getName());
        }
        return dto;
    }

    private ChatInvocation buildChatInvocation(Long modelConfigId, ChatRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "聊天请求不能为空");
        }
        ModelConfigPo model = modelConfigMapper.selectById(modelConfigId);
        if (model == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型配置不存在");
        }
        if (!Objects.equals(model.getEnabled(), 1)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "模型配置未启用: " + modelConfigId);
        }
        ProviderPo provider = providerMapper.selectById(model.getProviderId());
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "提供商不存在");
        }
        if (!Objects.equals(provider.getEnabled(), 1)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "提供商未启用: " + provider.getId());
        }
        request.setModel(model.getModelId());
        return new ChatInvocation(provider, request);
    }

    private record ChatInvocation(ProviderPo provider, ChatRequest request) {
    }
}
