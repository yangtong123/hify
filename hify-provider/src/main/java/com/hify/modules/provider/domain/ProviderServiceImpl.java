package com.hify.modules.provider.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.http.LlmHttpClient;
import com.hify.common.web.PageResult;
import com.hify.modules.provider.api.ProviderService;
import com.hify.modules.provider.api.dto.ConnectionTestResult;
import com.hify.modules.provider.api.dto.ProviderDetailResponse;
import com.hify.modules.provider.api.dto.ProviderDetailResponse.HealthSummary;
import com.hify.modules.provider.api.dto.ProviderDetailResponse.ModelConfigResponse;
import com.hify.modules.provider.api.dto.ProviderQuery;
import com.hify.modules.provider.api.dto.ProviderRequest;
import com.hify.modules.provider.api.dto.ProviderResponse;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderServiceImpl implements ProviderService {

    private final ProviderMapper providerMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final ProviderHealthCheckMapper healthCheckMapper;
    private final LlmHttpClient llmHttpClient;
    private final ObjectMapper objectMapper;

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
    public ConnectionTestResult testConnection(Long id) {
        ProviderPo provider = providerMapper.selectById(id);
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "提供商不存在");
        }

        String baseUrl = stripTrailingSlash(provider.getBaseUrl());
        String providerType = provider.getType();
        AuthConfig authConfig = provider.getAuthConfig();

        String url = buildTestUrl(baseUrl, providerType);
        Map<String, String> headers = buildTestHeaders(providerType, authConfig);

        long start = System.currentTimeMillis();
        try {
            String responseBody = llmHttpClient.get(url, headers, 10);
            long latencyMs = System.currentTimeMillis() - start;
            int modelCount = parseModelCount(responseBody, providerType);
            log.info("Connection test OK: provider={}, latency={}ms, models={}", provider.getName(), latencyMs, modelCount);
            return ConnectionTestResult.success(latencyMs, modelCount);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.warn("Connection test failed: provider={}, latency={}ms, error={}", provider.getName(), latencyMs, e.getMessage());
            return ConnectionTestResult.fail(e.getMessage());
        }
    }

    private String buildTestUrl(String baseUrl, String providerType) {
        return switch (providerType.toLowerCase()) {
            case "openai", "openai_compatible" -> baseUrl + "/v1/models";
            case "anthropic" -> baseUrl + "/v1/models";
            case "ollama" -> baseUrl + "/api/tags";
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "不支持的提供商类型: " + providerType);
        };
    }

    private Map<String, String> buildTestHeaders(String providerType, AuthConfig authConfig) {
        Map<String, String> headers = new HashMap<>();
        String apiKey = authConfig != null ? authConfig.getApiKey() : null;
        String normalizedType = providerType.toLowerCase();
        switch (normalizedType) {
            case "openai", "openai_compatible" -> {
                if (apiKey != null && !apiKey.isBlank()) {
                    headers.put("Authorization", "Bearer " + apiKey);
                }
            }
            case "anthropic" -> {
                if (apiKey != null && !apiKey.isBlank()) {
                    headers.put("x-api-key", apiKey);
                }
                headers.put("anthropic-version", "2023-06-01");
            }
            case "ollama" -> {
                // no auth
            }
        }
        return headers;
    }

    private int parseModelCount(String responseBody, String providerType) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if ("ollama".equalsIgnoreCase(providerType)) {
                JsonNode models = root.get("models");
                return models != null ? models.size() : 0;
            }
            JsonNode data = root.get("data");
            return data != null ? data.size() : 0;
        } catch (Exception e) {
            log.warn("Failed to parse model count from response", e);
            return 0;
        }
    }

    private String stripTrailingSlash(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
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
}
