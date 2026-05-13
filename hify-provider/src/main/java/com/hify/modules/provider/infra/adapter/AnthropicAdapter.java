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
public class AnthropicAdapter implements ProviderAdapter {

    private final LlmHttpClient llmHttpClient;
    private final ObjectMapper objectMapper;

    @Override
    public String getType() {
        return "anthropic";
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

    private String buildTestUrl(String baseUrl) {
        return stripTrailingSlash(baseUrl) + "/v1/models";
    }

    private String buildChatUrl(String baseUrl) {
        return stripTrailingSlash(baseUrl) + "/v1/messages";
    }

    private Map<String, String> buildHeaders(AuthConfig authConfig) {
        Map<String, String> headers = new HashMap<>();
        String apiKey = authConfig != null ? authConfig.getApiKey() : null;
        if (apiKey != null && !apiKey.isBlank()) {
            headers.put("x-api-key", apiKey);
        }
        headers.put("anthropic-version", "2023-06-01");
        return headers;
    }

    private int parseModelCount(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.get("data");
            return data != null ? data.size() : 0;
        } catch (Exception e) {
            log.warn("Failed to parse model count from response", e);
            return 0;
        }
    }

    private List<String> parseModelNames(String responseBody) {
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

    private Map<String, Object> buildChatBody(ChatRequest request, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel());
        body.put("messages", buildMessages(request.getMessages()));
        body.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 4096);
        body.put("stream", stream);
        putIfPresent(body, "system", findSystemPrompt(request.getMessages()));
        putIfPresent(body, "temperature", request.getTemperature());
        putIfPresent(body, "top_p", request.getTopP());
        putIfPresent(body, "stop_sequences", request.getStop());
        putIfPresent(body, "tools", buildTools(request.getTools()));
        if (request.getExtraParams() != null) {
            body.putAll(request.getExtraParams());
        }
        return body;
    }

    private List<Map<String, Object>> buildMessages(List<ChatRequest.Message> messages) {
        if (messages == null) {
            return List.of();
        }
        List<Map<String, Object>> converted = new ArrayList<>();
        for (ChatRequest.Message message : messages) {
            if ("system".equals(message.getRole())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", message.getRole());
            item.put("content", message.getContent() != null ? message.getContent() : "");
            converted.add(item);
        }
        return converted;
    }

    private String findSystemPrompt(List<ChatRequest.Message> messages) {
        if (messages == null) {
            return null;
        }
        return messages.stream()
                .filter(message -> "system".equals(message.getRole()))
                .map(ChatRequest.Message::getContent)
                .filter(content -> content != null && !content.isBlank())
                .findFirst()
                .orElse(null);
    }

    private List<Map<String, Object>> buildTools(List<ChatRequest.Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> converted = new ArrayList<>();
        for (ChatRequest.Tool tool : tools) {
            if (tool.getFunction() == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", tool.getFunction().getName());
            item.put("description", tool.getFunction().getDescription());
            item.put("input_schema", tool.getFunction().getParameters());
            converted.add(item);
        }
        return converted;
    }

    private ChatResponse parseChatResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            ChatResponse response = new ChatResponse();
            response.setId(text(root, "id"));
            response.setModel(text(root, "model"));
            response.setRole(text(root, "role"));
            response.setContent(parseTextContent(root.get("content")));
            response.setFinishReason(text(root, "stop_reason"));
            response.setUsage(parseUsage(root.get("usage")));
            return response;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse Anthropic chat response", e);
        }
    }

    private ChatResponse parseStreamLine(String line) {
        if (!line.startsWith("data:")) {
            return null;
        }
        String data = line.substring("data:".length()).trim();
        if (data.isBlank() || "[DONE]".equals(data)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(data);
            String type = text(root, "type");
            ChatResponse response = new ChatResponse();
            if ("message_start".equals(type)) {
                JsonNode message = root.get("message");
                response.setId(text(message, "id"));
                response.setModel(text(message, "model"));
                response.setRole(text(message, "role"));
                response.setUsage(parseUsage(message != null ? message.get("usage") : null));
                return response;
            }
            if ("content_block_delta".equals(type)) {
                JsonNode delta = root.get("delta");
                response.setContent(text(delta, "text"));
                return response.getContent() != null ? response : null;
            }
            if ("message_delta".equals(type)) {
                JsonNode delta = root.get("delta");
                response.setFinishReason(text(delta, "stop_reason"));
                response.setUsage(parseUsage(root.get("usage")));
                return response;
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to parse Anthropic stream line: {}", line, e);
            return null;
        }
    }

    private String parseTextContent(JsonNode contentNode) {
        if (contentNode == null || !contentNode.isArray()) {
            return null;
        }
        StringBuilder content = new StringBuilder();
        for (JsonNode node : contentNode) {
            if ("text".equals(text(node, "type"))) {
                content.append(text(node, "text"));
            }
        }
        return content.toString();
    }

    private ChatResponse.Usage parseUsage(JsonNode usageNode) {
        if (usageNode == null || usageNode.isNull()) {
            return null;
        }
        ChatResponse.Usage usage = new ChatResponse.Usage();
        usage.setPromptTokens(intValue(usageNode, "input_tokens"));
        usage.setCompletionTokens(intValue(usageNode, "output_tokens"));
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
