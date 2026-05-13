package com.hify.modules.chat.web;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.modules.chat.api.ChatService;
import com.hify.modules.chat.api.dto.ChatCompletionResponse;
import com.hify.modules.chat.api.dto.ChatMessageQuery;
import com.hify.modules.chat.api.dto.ChatMessageResponse;
import com.hify.modules.chat.api.dto.ChatSendRequest;
import com.hify.modules.chat.api.dto.ChatSessionQuery;
import com.hify.modules.chat.api.dto.ChatSessionResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/chats")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/messages")
    public Result<ChatCompletionResponse> completeMessage(@Valid @RequestBody ChatSendRequest request) {
        return Result.ok(chatService.completeMessage(request));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(@Valid @RequestBody ChatSendRequest request) {
        return chatService.sendMessage(request);
    }

    @GetMapping
    public PageResult<List<ChatSessionResponse>> listSessions(@Valid ChatSessionQuery query) {
        return chatService.listSessions(query);
    }

    @GetMapping("/{sessionId}")
    public Result<ChatSessionResponse> getSession(@PathVariable Long sessionId) {
        return Result.ok(chatService.getSession(sessionId));
    }

    @GetMapping("/{sessionId}/messages")
    public Result<List<ChatMessageResponse>> listMessages(@PathVariable Long sessionId,
                                                           @Valid ChatMessageQuery query) {
        return Result.ok(chatService.listMessages(sessionId, query));
    }

    @DeleteMapping("/{sessionId}")
    public Result<Void> archiveSession(@PathVariable Long sessionId) {
        chatService.archiveSession(sessionId);
        return Result.ok();
    }
}
