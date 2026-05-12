package com.hify.modules.provider.infra.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmHttpClient;
import com.hify.modules.provider.api.dto.ConnectionTestResult;
import com.hify.modules.provider.infra.po.AuthConfig;
import com.hify.modules.provider.infra.po.ProviderPo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiAdapter implements ProviderAdapter {

    private final LlmHttpClient llmHttpClient;
    private final ObjectMapper objectMapper;

    @Override
    public String getType() {
        return "openai";
    }

    @Override
    public ConnectionTestResult testConnection(ProviderPo provider) {
        String url = buildTestUrl(provider.getBaseUrl());
        Map<String, String> headers = buildHeaders(provider.getAuthConfig());

        long start = System.currentTimeMillis();
        try {
            String responseBody = llmHttpClient.get(url, headers, 10);
            long latencyMs = System.currentTimeMillis() - start;
            int modelCount = parseModelCount(responseBody);
            log.info("Connection test OK: provider={}, latency={}ms, models={}", provider.getName(), latencyMs, modelCount);
            return ConnectionTestResult.success(latencyMs, modelCount);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.warn("Connection test failed: provider={}, latency={}ms, error={}", provider.getName(), latencyMs, e.getMessage());
            return ConnectionTestResult.fail(e.getMessage());
        }
    }

    @Override
    public List<String> listModels(ProviderPo provider) {
        String url = buildTestUrl(provider.getBaseUrl());
        Map<String, String> headers = buildHeaders(provider.getAuthConfig());

        String responseBody = llmHttpClient.get(url, headers, 10);
        return parseModelNames(responseBody);
    }

    protected String buildTestUrl(String baseUrl) {
        return stripTrailingSlash(baseUrl) + "/v1/models";
    }

    protected Map<String, String> buildHeaders(AuthConfig authConfig) {
        Map<String, String> headers = new HashMap<>();
        String apiKey = authConfig != null ? authConfig.getApiKey() : null;
        if (apiKey != null && !apiKey.isBlank()) {
            headers.put("Authorization", "Bearer " + apiKey);
        }
        return headers;
    }

    protected int parseModelCount(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.get("data");
            return data != null ? data.size() : 0;
        } catch (Exception e) {
            log.warn("Failed to parse model count from response", e);
            return 0;
        }
    }

    protected List<String> parseModelNames(String responseBody) {
        List<String> names = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.get("data");
            if (data != null) {
                for (JsonNode node : data) {
                    if (node.has("id")) {
                        names.add(node.get("id").asText());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse model names from response", e);
        }
        return names;
    }

    private String stripTrailingSlash(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
