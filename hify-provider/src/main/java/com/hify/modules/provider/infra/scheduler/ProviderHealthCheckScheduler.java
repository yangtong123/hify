package com.hify.modules.provider.infra.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.modules.provider.api.ProviderService;
import com.hify.modules.provider.api.dto.ConnectionTestResult;
import com.hify.modules.provider.infra.mapper.ProviderHealthCheckMapper;
import com.hify.modules.provider.infra.mapper.ProviderMapper;
import com.hify.modules.provider.infra.po.ProviderHealthCheckPo;
import com.hify.modules.provider.infra.po.ProviderPo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class ProviderHealthCheckScheduler {

    private static final int DOWN_THRESHOLD = 3;

    private final ProviderMapper providerMapper;
    private final ProviderHealthCheckMapper healthCheckMapper;
    private final ProviderService providerService;

    @Autowired(required = false)
    private CacheManager cacheManager;

    public ProviderHealthCheckScheduler(ProviderMapper providerMapper,
                                         ProviderHealthCheckMapper healthCheckMapper,
                                         ProviderService providerService) {
        this.providerMapper = providerMapper;
        this.healthCheckMapper = healthCheckMapper;
        this.providerService = providerService;
    }

    @Async("asyncExecutor")
    @Scheduled(fixedRate = 60000)
    public void checkAllProviders() {
        List<ProviderPo> providers = providerMapper.selectList(
                new LambdaQueryWrapper<ProviderPo>()
                        .eq(ProviderPo::getEnabled, 1));

        if (providers.isEmpty()) {
            return;
        }

        log.info("Health check started for {} enabled providers", providers.size());
        for (ProviderPo provider : providers) {
            checkOne(provider);
        }
        log.info("Health check completed for {} providers", providers.size());

        if (cacheManager != null) {
            var cache = cacheManager.getCache("provider-cache");
            if (cache != null) {
                cache.clear();
            }
        }
    }

    private void checkOne(ProviderPo provider) {
        try {
            ConnectionTestResult result = providerService.testConnection(provider.getId());
            if (result.isSuccess()) {
                updateHealthSuccess(provider.getId(), result);
            } else {
                updateHealthFailure(provider.getId(), result.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("Health check exception: provider={}", provider.getName(), e);
            updateHealthFailure(provider.getId(), e.getMessage());
        }
    }

    private void updateHealthSuccess(Long providerId, ConnectionTestResult result) {
        ProviderHealthCheckPo record = findOrCreateRecord(providerId);
        record.setStatus("UP");
        record.setLatencyMs((int) result.getLatencyMs());
        record.setLastSuccessAt(LocalDateTime.now());
        record.setFailCount(0);
        record.setErrorMessage(null);
        record.setLastCheckAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        saveRecord(record);
    }

    private void updateHealthFailure(Long providerId, String errorMessage) {
        ProviderHealthCheckPo record = findOrCreateRecord(providerId);
        int newFailCount = (record.getFailCount() != null ? record.getFailCount() : 0) + 1;
        record.setStatus(newFailCount >= DOWN_THRESHOLD ? "DOWN" : "UP");
        record.setFailCount(newFailCount);
        record.setErrorMessage(errorMessage);
        record.setLastCheckAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        saveRecord(record);
    }

    private void saveRecord(ProviderHealthCheckPo record) {
        if (record.getId() != null) {
            healthCheckMapper.updateById(record);
        } else {
            healthCheckMapper.insert(record);
        }
    }

    private ProviderHealthCheckPo findOrCreateRecord(Long providerId) {
        ProviderHealthCheckPo existing = healthCheckMapper.selectOne(
                new LambdaQueryWrapper<ProviderHealthCheckPo>()
                        .eq(ProviderHealthCheckPo::getProviderId, providerId));
        if (existing != null) {
            return existing;
        }
        ProviderHealthCheckPo record = new ProviderHealthCheckPo();
        record.setProviderId(providerId);
        record.setStatus("UP");
        record.setFailCount(0);
        return record;
    }
}
