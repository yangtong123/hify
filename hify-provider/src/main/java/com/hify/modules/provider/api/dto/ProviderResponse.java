package com.hify.modules.provider.api.dto;

import com.hify.modules.provider.infra.po.AuthConfig;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProviderResponse {

    private Long id;
    private String name;
    private String type;
    private String baseUrl;
    private Integer enabled;
    private AuthConfig authConfig;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Masked api key for display, extracted from authConfig. */
    private String apiKey;

    /** Health status (UP/DOWN/DEGRADED/UNKNOWN). Only populated in list queries. */
    private String healthStatus;

    /** Last health check latency in ms. Only populated in list queries. */
    private Integer healthLatencyMs;

    /** Number of enabled models for this provider. Only populated in list queries. */
    private Integer modelCount;
}
