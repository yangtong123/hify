package com.hify.modules.provider.infra.po;

import lombok.Data;

import java.util.Map;

@Data
public class AuthConfig {

    private String authType;
    private String apiKey;
    private String apiVersion;
    private Map<String, String> customHeaders;
}
