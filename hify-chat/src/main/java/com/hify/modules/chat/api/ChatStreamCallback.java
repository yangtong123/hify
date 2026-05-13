package com.hify.modules.chat.api;

import com.hify.modules.chat.api.dto.ChatCompletionResponse;
import com.hify.modules.chat.api.dto.ChatSessionResponse;
import com.hify.modules.chat.api.dto.ChatStreamChunk;

public interface ChatStreamCallback {

    void onSession(ChatSessionResponse session);

    void onDelta(ChatStreamChunk chunk);

    void onComplete(ChatCompletionResponse response);

    void onError(Throwable throwable);
}
