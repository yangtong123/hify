package com.hify.modules.provider.api.dto;

import com.hify.modules.provider.infra.po.AuthConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProviderRequest {

    @NotBlank(message = "提供商名称不能为空")
    private String name;

    @NotBlank(message = "提供商类型不能为空")
    private String type;

    @NotBlank(message = "API 根地址不能为空")
    private String baseUrl;

    private Integer enabled;

    private AuthConfig authConfig;
}
