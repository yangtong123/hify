package com.hify.modules.chat.web;

import com.hify.common.web.Result;
import com.hify.modules.chat.api.ChatService;
import com.hify.modules.chat.api.dto.ChatCompletionResponse;
import com.hify.modules.chat.api.dto.ChatSendRequest;
import com.hify.modules.chat.api.dto.ChatSessionResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    private ChatController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatController(chatService);
    }

    @Test
    void completeMessageShouldWrapServiceResponse() {
        ChatSendRequest request = new ChatSendRequest();
        request.setAgentId(1L);
        request.setUserId("u-1");
        request.setContent("Hello");

        ChatCompletionResponse completion = new ChatCompletionResponse();
        ChatSessionResponse session = new ChatSessionResponse();
        session.setId(2L);
        completion.setSession(session);
        when(chatService.completeMessage(request)).thenReturn(completion);

        Result<ChatCompletionResponse> result = controller.completeMessage(request);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isSameAs(completion);
        verify(chatService).completeMessage(request);
    }

    @Test
    void sendMessageShouldDelegateSseToService() {
        ChatSendRequest request = new ChatSendRequest();
        request.setAgentId(1L);
        request.setUserId("u-1");
        request.setContent("Hello");
        SseEmitter emitter = new SseEmitter();
        when(chatService.sendMessage(request)).thenReturn(emitter);

        SseEmitter result = controller.sendMessage(request);

        assertThat(result).isSameAs(emitter);
        verify(chatService).sendMessage(request);
    }

    @Test
    void archiveSessionShouldDelegateToService() {
        Result<Void> result = controller.archiveSession(9L);

        assertThat(result.getCode()).isEqualTo(200);
        verify(chatService).archiveSession(9L);
    }
}
