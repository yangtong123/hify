package com.hify.modules.chat.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSessionResponse {

    private Long id;

    private Long agentId;

    private String title;

    private String userId;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
