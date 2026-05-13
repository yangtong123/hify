package com.hify.modules.provider.api.dto;

import lombok.Data;

@Data
public class ModelConfigDto {

    private Long id;

    private Long providerId;

    private String providerName;

    private String name;

    private String modelId;

    private Integer contextSize;

    private Integer enabled;
}
