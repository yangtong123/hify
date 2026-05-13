package com.hify.modules.chat.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ChatMessageResponse {

    private Long id;

    private Long sessionId;

    private String role;

    private String content;

    private Integer tokenCount;

    private Object toolCalls;

    private Map<String, Object> metadata;

    private LocalDateTime createdAt;
}
