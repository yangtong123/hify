package com.hify.modules.provider.api.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProviderDetailResponse extends ProviderResponse {

    private List<ModelConfigResponse> models;
    private HealthSummary health;

    @Data
    public static class ModelConfigResponse {
        private Long id;
        private String name;
        private String modelId;
        private Integer contextSize;
        private Integer enabled;
        private Object extraParams;
    }

    @Data
    public static class HealthSummary {
        private String status;
        private LocalDateTime lastCheckAt;
        private LocalDateTime lastSuccessAt;
        private Integer latencyMs;
        private Integer failCount;
    }
}
