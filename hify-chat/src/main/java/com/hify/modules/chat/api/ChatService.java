package com.hify.modules.chat.api;

import com.hify.common.web.PageResult;
import com.hify.modules.chat.api.dto.ChatCompletionResponse;
import com.hify.modules.chat.api.dto.ChatMessageQuery;
import com.hify.modules.chat.api.dto.ChatMessageResponse;
import com.hify.modules.chat.api.dto.ChatSendRequest;
import com.hify.modules.chat.api.dto.ChatSessionQuery;
import com.hify.modules.chat.api.dto.ChatSessionResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface ChatService {

    ChatCompletionResponse completeMessage(ChatSendRequest request);

    SseEmitter sendMessage(ChatSendRequest request);

    void streamMessage(ChatSendRequest request, ChatStreamCallback callback);

    PageResult<List<ChatSessionResponse>> listSessions(ChatSessionQuery query);

    ChatSessionResponse getSession(Long sessionId);

    List<ChatMessageResponse> listMessages(Long sessionId, ChatMessageQuery query);

    void archiveSession(Long sessionId);
}
