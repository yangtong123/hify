package com.hify.modules.provider.infra.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmHttpClient;
import com.hify.modules.provider.api.dto.ChatRequest;
import com.hify.modules.provider.api.dto.ChatResponse;
import com.hify.modules.provider.api.dto.ConnectionTestResult;
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

    protected String buildTestUrl(String baseUrl) {
        return stripTrailingSlash(baseUrl) + "/v1/models";
    }

    protected String buildChatUrl(String baseUrl) {
        String normalized = stripTrailingSlash(baseUrl);
        if (normalized != null && normalized.endsWith("/v1")) {
            return normalized + "/chat/completions";
        }
        return normalized + "/v1/chat/completions";
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

    protected Map<String, Object> buildChatBody(ChatRequest request, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel());
        body.put("messages", request.getMessages());
        body.put("stream", stream);
        putIfPresent(body, "temperature", request.getTemperature());
        putIfPresent(body, "top_p", request.getTopP());
        putIfPresent(body, "max_tokens", request.getMaxTokens());
        putIfPresent(body, "stop", request.getStop());
        putIfPresent(body, "tools", request.getTools());
        if (request.getExtraParams() != null) {
            body.putAll(request.getExtraParams());
        }
        return body;
    }

    protected ChatResponse parseChatResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            ChatResponse response = new ChatResponse();
            response.setId(text(root, "id"));
            response.setModel(text(root, "model"));

            JsonNode choice = firstChoice(root);
            if (choice != null) {
                JsonNode message = choice.get("message");
                if (message != null) {
                    response.setRole(text(message, "role"));
                    response.setContent(text(message, "content"));
                    response.setToolCalls(parseToolCalls(message.get("tool_calls")));
                }
                response.setFinishReason(text(choice, "finish_reason"));
            }
            response.setUsage(parseUsage(root.get("usage")));
            return response;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse OpenAI chat response", e);
        }
    }

    protected ChatResponse parseStreamLine(String line) {
        String data = line;
        if (line.startsWith("data:")) {
            data = line.substring("data:".length()).trim();
        }
        if (data.isBlank() || "[DONE]".equals(data)) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode choice = firstChoice(root);
            if (choice == null) {
                return null;
            }
            ChatResponse response = new ChatResponse();
            response.setId(text(root, "id"));
            response.setModel(text(root, "model"));
            response.setFinishReason(text(choice, "finish_reason"));

            JsonNode delta = choice.get("delta");
            if (delta != null) {
                response.setRole(text(delta, "role"));
                response.setContent(text(delta, "content"));
                response.setToolCalls(parseToolCalls(delta.get("tool_calls")));
            }
            response.setUsage(parseUsage(root.get("usage")));
            return response;
        } catch (Exception e) {
            log.warn("Failed to parse OpenAI stream line: {}", line, e);
            return null;
        }
    }

    private JsonNode firstChoice(JsonNode root) {
        JsonNode choices = root.get("choices");
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        return choices.get(0);
    }

    private ChatResponse.Usage parseUsage(JsonNode usageNode) {
        if (usageNode == null || usageNode.isNull()) {
            return null;
        }
        ChatResponse.Usage usage = new ChatResponse.Usage();
        usage.setPromptTokens(intValue(usageNode, "prompt_tokens"));
        usage.setCompletionTokens(intValue(usageNode, "completion_tokens"));
        usage.setTotalTokens(intValue(usageNode, "total_tokens"));
        return usage;
    }

    private List<ChatResponse.ToolCall> parseToolCalls(JsonNode toolCallsNode) {
        if (toolCallsNode == null || !toolCallsNode.isArray()) {
            return null;
        }
        List<ChatResponse.ToolCall> toolCalls = new ArrayList<>();
        for (JsonNode node : toolCallsNode) {
            try {
                toolCalls.add(objectMapper.treeToValue(node, ChatResponse.ToolCall.class));
            } catch (Exception e) {
                log.warn("Failed to parse tool call from chat response", e);
            }
        }
        return toolCalls;
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
