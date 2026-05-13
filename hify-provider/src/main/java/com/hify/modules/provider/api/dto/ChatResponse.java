package com.hify.modules.provider.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ChatResponse {

    private String id;

    private String model;

    private String role;

    private String content;

    @JsonProperty("finish_reason")
    private String finishReason;

    private Usage usage;

    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;

    private Map<String, Object> metadata;

    public static ChatResponse delta(String content) {
        ChatResponse response = new ChatResponse();
        response.setContent(content);
        return response;
    }

    @Data
    public static class Usage {

        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }

    @Data
    public static class ToolCall {

        private String id;

        private String type;

        private FunctionCall function;
    }

    @Data
    public static class FunctionCall {

        private String name;

        private String arguments;
    }
}
