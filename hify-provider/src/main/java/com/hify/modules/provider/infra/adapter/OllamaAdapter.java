package com.hify.modules.provider.infra.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmHttpClient;
import com.hify.modules.provider.api.dto.ChatRequest;
import com.hify.modules.provider.api.dto.ChatResponse;
import com.hify.modules.provider.api.dto.ConnectionTestResult;
import com.hify.modules.provider.api.dto.EmbeddingRequest;
import com.hify.modules.provider.api.dto.EmbeddingResponse;
import com.hify.modules.provider.infra.po.AuthConfig;
import com.hify.modules.provider.infra.po.ProviderPo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaAdapter implements ProviderAdapter {

    private final LlmHttpClient llmHttpClient;
    private final ObjectMapper objectMapper;

    @Override
    public String getType() {
        return "ollama";
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

    @Override
    public ChatResponse chat(ProviderPo provider, ChatRequest request) {
        String url = buildChatUrl(provider.getBaseUrl());
        Map<String, String> headers = buildHeaders(provider.getAuthConfig());
        String responseBody = llmHttpClient.post(url, headers, toJson(buildChatBody(request, false)));
        return parseChatResponse(responseBody);
    }

    @Override
    public void streamChat(ProviderPo provider, ChatRequest request, Consumer<ChatResponse> chunkConsumer) {
        String url = buildChatUrl(provider.getBaseUrl());
        Map<String, String> headers = buildHeaders(provider.getAuthConfig());
        llmHttpClient.stream(url, headers, toJson(buildChatBody(request, true)), line -> {
            ChatResponse response = parseStreamLine(line);
            if (response != null) {
                chunkConsumer.accept(response);
            }
        });
    }

    @Override
    public EmbeddingResponse embed(ProviderPo provider, EmbeddingRequest request) {
        String url = buildEmbedUrl(provider.getBaseUrl());
        Map<String, String> headers = buildHeaders(provider.getAuthConfig());
        String responseBody = llmHttpClient.post(url, headers, toJson(buildEmbedBody(request)));
        return parseEmbedResponse(responseBody, request.getModel());
    }

    private String buildTestUrl(String baseUrl) {
        return stripTrailingSlash(baseUrl) + "/api/tags";
    }

    private String buildChatUrl(String baseUrl) {
        return stripTrailingSlash(baseUrl) + "/api/chat";
    }

    private String buildEmbedUrl(String baseUrl) {
        return stripTrailingSlash(baseUrl) + "/api/embed";
    }

    private Map<String, String> buildHeaders(AuthConfig authConfig) {
        return new HashMap<>();
    }

    private int parseModelCount(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode models = root.get("models");
            return models != null ? models.size() : 0;
        } catch (Exception e) {
            log.warn("Failed to parse model count from response", e);
            return 0;
        }
    }

    private List<String> parseModelNames(String responseBody) {
        List<String> names = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode models = root.get("models");
            if (models != null) {
                for (JsonNode node : models) {
                    if (node.has("name")) {
                        names.add(node.get("name").asText());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse model names from response", e);
        }
        return names;
    }

    private Map<String, Object> buildChatBody(ChatRequest request, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel());
        body.put("messages", request.getMessages());
        body.put("stream", stream);
        putIfPresent(body, "tools", request.getTools());
        Map<String, Object> options = buildOptions(request);
        if (!options.isEmpty()) {
            body.put("options", options);
        }
        if (request.getExtraParams() != null) {
            body.putAll(request.getExtraParams());
        }
        return body;
    }

    private Map<String, Object> buildOptions(ChatRequest request) {
        Map<String, Object> options = new LinkedHashMap<>();
        putIfPresent(options, "temperature", request.getTemperature());
        putIfPresent(options, "top_p", request.getTopP());
        putIfPresent(options, "num_predict", request.getMaxTokens());
        putIfPresent(options, "stop", request.getStop());
        return options;
    }

    private Map<String, Object> buildEmbedBody(EmbeddingRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel());
        body.put("input", request.getInputs());
        return body;
    }

    private ChatResponse parseChatResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return parseChatJson(root);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse Ollama chat response", e);
        }
    }

    private EmbeddingResponse parseEmbedResponse(String responseBody, String model) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            EmbeddingResponse response = new EmbeddingResponse();
            response.setModel(text(root, "model") != null ? text(root, "model") : model);
            List<List<Double>> embeddings = new ArrayList<>();
            JsonNode values = root.get("embeddings");
            if (values != null && values.isArray()) {
                for (JsonNode item : values) {
                    embeddings.add(toDoubleList(item));
                }
            }
            response.setEmbeddings(embeddings);
            return response;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse Ollama embedding response", e);
        }
    }

    private List<Double> toDoubleList(JsonNode node) {
        List<Double> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode value : node) {
                values.add(value.asDouble());
            }
        }
        return values;
    }

    private ChatResponse parseStreamLine(String line) {
        try {
            return parseChatJson(objectMapper.readTree(line));
        } catch (Exception e) {
            log.warn("Failed to parse Ollama stream line: {}", line, e);
            return null;
        }
    }

    private ChatResponse parseChatJson(JsonNode root) {
        ChatResponse response = new ChatResponse();
        response.setModel(text(root, "model"));
        JsonNode message = root.get("message");
        if (message != null) {
            response.setRole(text(message, "role"));
            response.setContent(text(message, "content"));
        }
        if (root.path("done").asBoolean(false)) {
            response.setFinishReason(text(root, "done_reason"));
            response.setUsage(parseUsage(root));
        }
        return response;
    }

    private ChatResponse.Usage parseUsage(JsonNode root) {
        ChatResponse.Usage usage = new ChatResponse.Usage();
        usage.setPromptTokens(intValue(root, "prompt_eval_count"));
        usage.setCompletionTokens(intValue(root, "eval_count"));
        Integer promptTokens = usage.getPromptTokens();
        Integer completionTokens = usage.getCompletionTokens();
        if (promptTokens != null || completionTokens != null) {
            usage.setTotalTokens((promptTokens != null ? promptTokens : 0) + (completionTokens != null ? completionTokens : 0));
        }
        return usage;
    }

    private void putIfPresent(Map<String, Object> body, String key, Object value) {
        if (value != null) {
            body.put(key, value);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize chat request", e);
        }
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node != null ? node.get(fieldName) : null;
        return value != null && !value.isNull() ? value.asText() : null;
    }

    private Integer intValue(JsonNode node, String fieldName) {
        JsonNode value = node != null ? node.get(fieldName) : null;
        return value != null && value.isNumber() ? value.asInt() : null;
    }

    private String stripTrailingSlash(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
