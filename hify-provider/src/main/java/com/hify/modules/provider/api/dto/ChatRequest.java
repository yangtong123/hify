package com.hify.modules.provider.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ChatRequest {

    private String model;

    private List<Message> messages;

    private BigDecimal temperature;

    @JsonProperty("top_p")
    private BigDecimal topP;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private List<String> stop;

    private List<Tool> tools;

    private Map<String, Object> extraParams;

    @Data
    public static class Message {

        private String role;

        private String content;

        private String name;

        @JsonProperty("tool_call_id")
        private String toolCallId;

        @JsonProperty("tool_calls")
        private List<ToolCall> toolCalls;
    }

    @Data
    public static class Tool {

        private String type;

        private Function function;
    }

    @Data
    public static class Function {

        private String name;

        private String description;

        private Map<String, Object> parameters;
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
