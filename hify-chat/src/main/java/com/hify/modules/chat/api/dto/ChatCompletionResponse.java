package com.hify.modules.chat.api.dto;

import lombok.Data;

@Data
public class ChatCompletionResponse {

    private ChatSessionResponse session;

    private ChatMessageResponse userMessage;

    private ChatMessageResponse assistantMessage;
}
