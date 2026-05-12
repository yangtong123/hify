package com.hify.modules.provider.infra.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.modules.provider.api.ProviderService;
import com.hify.modules.provider.api.dto.ConnectionTestResult;
import com.hify.modules.provider.infra.mapper.ProviderHealthCheckMapper;
import com.hify.modules.provider.infra.mapper.ProviderMapper;
import com.hify.modules.provider.infra.po.ProviderHealthCheckPo;
import com.hify.modules.provider.infra.po.ProviderPo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderHealthCheckSchedulerTest {

    @Mock
    private ProviderMapper providerMapper;

    @Mock
    private ProviderHealthCheckMapper healthCheckMapper;

    @Mock
    private ProviderService providerService;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    private ProviderHealthCheckScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ProviderHealthCheckScheduler(providerMapper, healthCheckMapper, providerService, cacheManager);
    }

    @Test
    void checkAllProvidersShouldSkipWhenNoEnabledProviders() {
        when(providerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        scheduler.checkAllProviders();

        verifyNoInteractions(providerService, healthCheckMapper, cacheManager);
    }

    @Test
    void checkAllProvidersShouldInsertSuccessRecordWhenNoExistingHealth() {
        ProviderPo provider = provider(1L, "OpenAI");
        when(providerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(provider));
        when(providerService.testConnection(1L)).thenReturn(ConnectionTestResult.success(25, 2));
        when(healthCheckMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(cacheManager.getCache("provider-cache")).thenReturn(cache);

        scheduler.checkAllProviders();

        ArgumentCaptor<ProviderHealthCheckPo> captor = ArgumentCaptor.forClass(ProviderHealthCheckPo.class);
        verify(healthCheckMapper).insert(captor.capture());
        ProviderHealthCheckPo record = captor.getValue();
        assertThat(record.getProviderId()).isEqualTo(1L);
        assertThat(record.getStatus()).isEqualTo("UP");
        assertThat(record.getLatencyMs()).isEqualTo(25);
        assertThat(record.getFailCount()).isZero();
        assertThat(record.getErrorMessage()).isNull();
        assertThat(record.getLastCheckAt()).isNotNull();
        assertThat(record.getLastSuccessAt()).isNotNull();
        verify(healthCheckMapper, never()).updateById(any(ProviderHealthCheckPo.class));
    }

    @Test
    void checkAllProvidersShouldResetExistingRecordAfterSuccess() {
        ProviderPo provider = provider(1L, "OpenAI");
        ProviderHealthCheckPo existing = existingHealth(10L, 1L, "DOWN", 3, "timeout");
        when(providerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(provider));
        when(providerService.testConnection(1L)).thenReturn(ConnectionTestResult.success(42, 3));
        when(healthCheckMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(cacheManager.getCache("provider-cache")).thenReturn(cache);

        scheduler.checkAllProviders();

        ArgumentCaptor<ProviderHealthCheckPo> captor = ArgumentCaptor.forClass(ProviderHealthCheckPo.class);
        verify(healthCheckMapper).updateById(captor.capture());
        ProviderHealthCheckPo record = captor.getValue();
        assertThat(record.getId()).isEqualTo(10L);
        assertThat(record.getStatus()).isEqualTo("UP");
        assertThat(record.getLatencyMs()).isEqualTo(42);
        assertThat(record.getFailCount()).isZero();
        assertThat(record.getErrorMessage()).isNull();
        assertThat(record.getLastSuccessAt()).isAfter(LocalDateTime.parse("2026-05-11T10:00:00"));
    }

    @Test
    void checkAllProvidersShouldKeepStatusUpBeforeFailureThreshold() {
        ProviderPo provider = provider(1L, "OpenAI");
        ProviderHealthCheckPo existing = existingHealth(10L, 1L, "UP", 1, "previous failure");
        when(providerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(provider));
        when(providerService.testConnection(1L)).thenReturn(ConnectionTestResult.fail("timeout"));
        when(healthCheckMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(cacheManager.getCache("provider-cache")).thenReturn(cache);

        scheduler.checkAllProviders();

        ArgumentCaptor<ProviderHealthCheckPo> captor = ArgumentCaptor.forClass(ProviderHealthCheckPo.class);
        verify(healthCheckMapper).updateById(captor.capture());
        ProviderHealthCheckPo record = captor.getValue();
        assertThat(record.getStatus()).isEqualTo("UP");
        assertThat(record.getFailCount()).isEqualTo(2);
        assertThat(record.getErrorMessage()).isEqualTo("timeout");
        assertThat(record.getLastCheckAt()).isNotNull();
    }

    @Test
    void checkAllProvidersShouldMarkDownAtFailureThreshold() {
        ProviderPo provider = provider(1L, "OpenAI");
        ProviderHealthCheckPo existing = existingHealth(10L, 1L, "UP", 2, "previous failure");
        when(providerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(provider));
        when(providerService.testConnection(1L)).thenThrow(new IllegalStateException("connection refused"));
        when(healthCheckMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(cacheManager.getCache("provider-cache")).thenReturn(cache);

        scheduler.checkAllProviders();

        ArgumentCaptor<ProviderHealthCheckPo> captor = ArgumentCaptor.forClass(ProviderHealthCheckPo.class);
        verify(healthCheckMapper).updateById(captor.capture());
        ProviderHealthCheckPo record = captor.getValue();
        assertThat(record.getStatus()).isEqualTo("DOWN");
        assertThat(record.getFailCount()).isEqualTo(3);
        assertThat(record.getErrorMessage()).isEqualTo("connection refused");
    }

    private ProviderPo provider(Long id, String name) {
        ProviderPo provider = new ProviderPo();
        provider.setId(id);
        provider.setName(name);
        provider.setEnabled(1);
        return provider;
    }

    private ProviderHealthCheckPo existingHealth(Long id, Long providerId, String status, int failCount, String errorMessage) {
        ProviderHealthCheckPo record = new ProviderHealthCheckPo();
        record.setId(id);
        record.setProviderId(providerId);
        record.setStatus(status);
        record.setFailCount(failCount);
        record.setErrorMessage(errorMessage);
        record.setLastSuccessAt(LocalDateTime.parse("2026-05-11T10:00:00"));
        return record;
    }
}
