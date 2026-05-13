package com.hify.modules.chat.api.dto;

import lombok.Data;

@Data
public class ChatStreamChunk {

    private Long sessionId;

    private String content;

    private String finishReason;

    private Boolean done;
}
