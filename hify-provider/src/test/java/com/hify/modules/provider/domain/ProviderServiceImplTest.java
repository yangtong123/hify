package com.hify.modules.provider.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.metrics.HifyMetrics;
import com.hify.common.resilience.CircuitBreakerService;
import com.hify.common.web.PageResult;
import com.hify.modules.provider.api.dto.ConnectionTestResult;
import com.hify.modules.provider.api.dto.ProviderDetailResponse;
import com.hify.modules.provider.api.dto.ProviderQuery;
import com.hify.modules.provider.api.dto.ProviderRequest;
import com.hify.modules.provider.api.dto.ProviderResponse;
import com.hify.modules.provider.infra.adapter.ProviderAdapter;
import com.hify.modules.provider.infra.adapter.ProviderAdapterFactory;
import com.hify.modules.provider.infra.mapper.ModelConfigMapper;
import com.hify.modules.provider.infra.mapper.ProviderHealthCheckMapper;
import com.hify.modules.provider.infra.mapper.ProviderMapper;
import com.hify.modules.provider.infra.po.AuthConfig;
import com.hify.modules.provider.infra.po.ModelConfigPo;
import com.hify.modules.provider.infra.po.ProviderHealthCheckPo;
import com.hify.modules.provider.infra.po.ProviderPo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderServiceImplTest {

    @Mock
    private ProviderMapper providerMapper;

    @Mock
    private ModelConfigMapper modelConfigMapper;

    @Mock
    private ProviderHealthCheckMapper healthCheckMapper;

    @Mock
    private ProviderAdapterFactory adapterFactory;

    @Mock
    private ProviderAdapter providerAdapter;

    private ProviderServiceImpl providerService;

    @BeforeEach
    void setUp() {
        providerService = new ProviderServiceImpl(
                providerMapper,
                modelConfigMapper,
                healthCheckMapper,
                adapterFactory,
                new CircuitBreakerService(CircuitBreakerRegistry.ofDefaults(), new HifyMetrics(new SimpleMeterRegistry())),
                new HifyMetrics(new SimpleMeterRegistry()));
    }

    @Test
    void createShouldInsertProviderAndMaskApiKey() {
        ProviderRequest request = providerRequest("OpenAI", "openai", "sk-1234567890abcdef");
        when(providerMapper.exists(any())).thenReturn(false);
        doAnswer(invocation -> {
            ProviderPo po = invocation.getArgument(0);
            po.setId(10L);
            po.setCreatedAt(LocalDateTime.parse("2026-05-11T10:00:00"));
            po.setUpdatedAt(LocalDateTime.parse("2026-05-11T10:00:00"));
            return 1;
        }).when(providerMapper).insert(any(ProviderPo.class));

        ProviderResponse response = providerService.create(request);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getName()).isEqualTo("OpenAI");
        assertThat(response.getType()).isEqualTo("openai");
        assertThat(response.getApiKey()).isEqualTo("sk-1****cdef");

        ArgumentCaptor<ProviderPo> captor = ArgumentCaptor.forClass(ProviderPo.class);
        verify(providerMapper).insert(captor.capture());
        assertThat(captor.getValue())
                .extracting(ProviderPo::getName, ProviderPo::getBaseUrl, ProviderPo::getEnabled)
                .containsExactly("OpenAI", "https://api.openai.com", 1);
        assertThat(captor.getValue().getType()).isEqualTo("openai");
    }

    @Test
    void createShouldRejectDuplicateName() {
        ProviderRequest request = providerRequest("OpenAI", "openai", "sk-key");
        when(providerMapper.exists(any())).thenReturn(true);

        assertThatThrownBy(() -> providerService.create(request))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PARAM_ERROR);
    }

    @Test
    void updateShouldThrowWhenProviderNotFound() {
        when(providerMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> providerService.update(99L, providerRequest("OpenAI", "openai", "sk-key")))
                .isInstanceOf(BizException.class)
                .hasMessage("提供商不存在")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void getByIdShouldReturnModelsAndCurrentHealthSummary() {
        ProviderPo provider = providerPo(1L, "OpenAI", "openai", "sk-1234567890abcdef");
        when(providerMapper.selectById(1L)).thenReturn(provider);

        ModelConfigPo model = new ModelConfigPo();
        model.setId(101L);
        model.setName("GPT-4o");
        model.setModelId("gpt-4o");
        model.setContextSize(128000);
        model.setEnabled(1);
        when(modelConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(model));

        LocalDateTime now = LocalDateTime.parse("2026-05-11T11:00:00");
        LocalDateTime lastSuccessAt = now.minusMinutes(5);
        ProviderHealthCheckPo health = health("DOWN", 150, now);
        health.setLastSuccessAt(lastSuccessAt);
        health.setFailCount(3);
        when(healthCheckMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(health);

        ProviderDetailResponse detail = providerService.getById(1L);

        assertThat(detail.getId()).isEqualTo(1L);
        assertThat(detail.getApiKey()).isEqualTo("sk-1****cdef");
        assertThat(detail.getModels()).hasSize(1);
        assertThat(detail.getModels().get(0).getModelId()).isEqualTo("gpt-4o");
        assertThat(detail.getHealth().getStatus()).isEqualTo("down");
        assertThat(detail.getHealth().getLastCheckAt()).isEqualTo(now);
        assertThat(detail.getHealth().getLastSuccessAt()).isEqualTo(lastSuccessAt);
        assertThat(detail.getHealth().getLatencyMs()).isEqualTo(150);
        assertThat(detail.getHealth().getFailCount()).isEqualTo(3);
    }

    @Test
    void listShouldReturnMappedProvidersInMapperOrder() {
        ProviderQuery query = new ProviderQuery();
        query.setType("openai");
        query.setEnabled(1);

        Page<ProviderPo> pageResult = new Page<>(1, 20);
        pageResult.setRecords(List.of(
                providerPo(2L, "Azure OpenAI", "openai", "short"),
                providerPo(1L, "OpenAI", "openai", "sk-1234567890abcdef")));
        pageResult.setTotal(2);
        when(providerMapper.selectPage(any(), any())).thenReturn(pageResult);

        PageResult<List<ProviderResponse>> result = providerService.list(query);

        assertThat(result.getData()).extracting(ProviderResponse::getId).containsExactly(2L, 1L);
        assertThat(result.getData()).extracting(ProviderResponse::getApiKey).containsExactly("****", "sk-1****cdef");
        verify(providerMapper).selectPage(any(), any());
    }

    @Test
    void listAvailableModelConfigsShouldOnlyReturnModelsFromEnabledProviders() {
        ModelConfigPo enabledModel = modelPo(101L, 1L, "gpt-4o", 1);
        ModelConfigPo disabledProviderModel = modelPo(102L, 2L, "claude-sonnet", 1);
        when(modelConfigMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(enabledModel, disabledProviderModel));

        ProviderPo enabledProvider = providerPo(1L, "OpenAI", "openai", "sk-live-key");
        ProviderPo disabledProvider = providerPo(2L, "Anthropic", "anthropic", "sk-live-key");
        disabledProvider.setEnabled(0);
        when(providerMapper.selectBatchIds(List.of(1L, 2L)))
                .thenReturn(List.of(enabledProvider, disabledProvider));

        var result = providerService.listAvailableModelConfigs();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getModelId()).isEqualTo("gpt-4o");
        assertThat(result.get(0).getProviderName()).isEqualTo("OpenAI");
    }

    @Test
    void testConnectionShouldDelegateToAdapter() {
        ProviderPo provider = providerPo(1L, "OpenAI", "openai", "sk-live-key");
        provider.setBaseUrl("https://api.openai.com/");
        when(providerMapper.selectById(1L)).thenReturn(provider);
        when(adapterFactory.getAdapter("openai")).thenReturn(providerAdapter);
        when(providerAdapter.testConnection(provider))
                .thenReturn(ConnectionTestResult.success(150L, 2));
        when(providerAdapter.listModels(provider)).thenReturn(List.of("gpt-4o", "gpt-4.1"));
        when(modelConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        ConnectionTestResult result = providerService.testConnection(1L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getModelCount()).isEqualTo(2);
        assertThat(result.getLatencyMs()).isEqualTo(150L);
        verify(adapterFactory).getAdapter("openai");
        verify(providerAdapter).testConnection(provider);
    }

    @Test
    void testConnectionShouldSyncDiscoveredModels() {
        ProviderPo provider = providerPo(1L, "OpenAI", "openai", "sk-live-key");
        when(providerMapper.selectById(1L)).thenReturn(provider);
        when(adapterFactory.getAdapter("openai")).thenReturn(providerAdapter);
        when(providerAdapter.testConnection(provider)).thenReturn(ConnectionTestResult.success(150L, 1));
        when(providerAdapter.listModels(provider)).thenReturn(List.of("gpt-4o", "gpt-4.1"));
        when(modelConfigMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(modelPo(101L, 1L, "gpt-4o", 1)));

        ConnectionTestResult result = providerService.testConnection(1L);

        assertThat(result.getModelCount()).isEqualTo(2);
        ArgumentCaptor<ModelConfigPo> captor = ArgumentCaptor.forClass(ModelConfigPo.class);
        verify(modelConfigMapper).insert(captor.capture());
        assertThat(captor.getValue())
                .extracting(ModelConfigPo::getProviderId, ModelConfigPo::getName, ModelConfigPo::getModelId,
                        ModelConfigPo::getContextSize, ModelConfigPo::getEnabled)
                .containsExactly(1L, "gpt-4.1", "gpt-4.1", 128000, 1);
    }

    @Test
    void testConnectionShouldDelegateFailureFromAdapter() {
        ProviderPo provider = providerPo(1L, "Ollama", "ollama", null);
        provider.setBaseUrl("http://localhost:11434");
        when(providerMapper.selectById(1L)).thenReturn(provider);
        when(adapterFactory.getAdapter("ollama")).thenReturn(providerAdapter);
        when(providerAdapter.testConnection(provider))
                .thenReturn(ConnectionTestResult.fail("connection refused"));

        ConnectionTestResult result = providerService.testConnection(1L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("connection refused");
    }

    @Test
    void testConnectionShouldThrowWhenProviderMissing() {
        when(providerMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> providerService.testConnection(404L))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
        verifyNoInteractions(adapterFactory);
    }

    private ProviderRequest providerRequest(String name, String type, String apiKey) {
        ProviderRequest request = new ProviderRequest();
        request.setName(name);
        request.setType(type);
        request.setBaseUrl("https://api.openai.com");
        request.setEnabled(1);
        AuthConfig authConfig = new AuthConfig();
        authConfig.setAuthType("bearer");
        authConfig.setApiKey(apiKey);
        request.setAuthConfig(authConfig);
        return request;
    }

    private ProviderPo providerPo(Long id, String name, String type, String apiKey) {
        ProviderPo po = new ProviderPo();
        po.setId(id);
        po.setName(name);
        po.setType(type);
        po.setBaseUrl("https://api.openai.com");
        AuthConfig authConfig = new AuthConfig();
        authConfig.setAuthType("bearer");
        authConfig.setApiKey(apiKey);
        po.setAuthConfig(authConfig);
        po.setEnabled(1);
        po.setCreatedAt(LocalDateTime.parse("2026-05-11T10:00:00"));
        po.setUpdatedAt(LocalDateTime.parse("2026-05-11T10:00:00"));
        return po;
    }

    private ModelConfigPo modelPo(Long id, Long providerId, String modelId, Integer enabled) {
        ModelConfigPo po = new ModelConfigPo();
        po.setId(id);
        po.setProviderId(providerId);
        po.setName(modelId);
        po.setModelId(modelId);
        po.setContextSize(128000);
        po.setEnabled(enabled);
        return po;
    }

    private ProviderHealthCheckPo health(String status, int latencyMs, LocalDateTime lastCheckAt) {
        ProviderHealthCheckPo po = new ProviderHealthCheckPo();
        po.setStatus(status);
        po.setLatencyMs(latencyMs);
        po.setLastCheckAt(lastCheckAt);
        return po;
    }
}
