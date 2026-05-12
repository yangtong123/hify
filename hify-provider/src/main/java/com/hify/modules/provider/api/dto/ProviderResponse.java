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
}
