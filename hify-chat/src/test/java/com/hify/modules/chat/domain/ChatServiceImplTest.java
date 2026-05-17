package com.hify.modules.chat.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.agent.api.AgentService;
import com.hify.modules.agent.api.dto.AgentDetailResponse;
import com.hify.modules.chat.api.ChatStreamCallback;
import com.hify.modules.chat.api.dto.ChatCompletionResponse;
import com.hify.modules.chat.api.dto.ChatSendRequest;
import com.hify.modules.chat.api.dto.ChatSessionResponse;
import com.hify.modules.chat.api.dto.ChatStreamChunk;
import com.hify.modules.chat.infra.mapper.ChatMessageMapper;
import com.hify.modules.chat.infra.mapper.ChatSessionMapper;
import com.hify.modules.chat.infra.po.ChatMessagePo;
import com.hify.modules.chat.infra.po.ChatSessionPo;
import com.hify.modules.knowledge.api.KnowledgeService;
import com.hify.modules.provider.api.ProviderService;
import com.hify.modules.provider.api.dto.ChatRequest;
import com.hify.modules.provider.api.dto.ChatResponse;
import com.hify.modules.provider.api.dto.ModelConfigDto;
import com.hify.modules.workflow.api.WorkflowRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    private ChatSessionMapper sessionMapper;

    @Mock
    private ChatMessageMapper messageMapper;

    @Mock
    private AgentService agentService;

    @Mock
    private ProviderService providerService;

    @Mock
    private KnowledgeService knowledgeService;

    @Mock
    private WorkflowRunService workflowRunService;

    private final List<ChatSessionPo> sessions = new ArrayList<>();
    private final List<ChatMessagePo> messages = new ArrayList<>();
    private ChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        Executor directExecutor = Runnable::run;
        chatService = new ChatServiceImpl(sessionMapper, messageMapper, agentService, providerService,
                knowledgeService, workflowRunService, directExecutor, null);
        stubPersistence();
        lenient().when(agentService.getById(1L)).thenReturn(agent());
        lenient().when(providerService.getModelConfig(10L)).thenReturn(model());
        lenient().when(knowledgeService.retrieveForAgent(eq(1L), any())).thenReturn(List.of());
    }

    @Test
    void sendMessageShouldCreateSessionBuildContextAndStoreAssistantReply() {
        ChatResponse providerResponse = new ChatResponse();
        providerResponse.setId("chatcmpl-1");
        providerResponse.setModel("gpt-mock");
        providerResponse.setRole("assistant");
        providerResponse.setContent("Hello, human.");
        providerResponse.setFinishReason("stop");
        ChatResponse.Usage usage = new ChatResponse.Usage();
        usage.setPromptTokens(12);
        usage.setCompletionTokens(4);
        usage.setTotalTokens(16);
        providerResponse.setUsage(usage);
        when(providerService.chat(eq(10L), any(ChatRequest.class))).thenReturn(providerResponse);

        ChatCompletionResponse response = chatService.completeMessage(request(null, "Hello"));

        assertThat(response.getSession().getAgentId()).isEqualTo(1L);
        assertThat(response.getUserMessage().getRole()).isEqualTo("user");
        assertThat(response.getAssistantMessage().getContent()).isEqualTo("Hello, human.");
        assertThat(response.getAssistantMessage().getTokenCount()).isEqualTo(16);
        assertThat(messages).extracting(ChatMessagePo::getRole).containsExactly("user", "assistant");

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(providerService).chat(eq(10L), requestCaptor.capture());
        ChatRequest providerRequest = requestCaptor.getValue();
        assertThat(providerRequest.getModel()).isEqualTo("gpt-mock");
        assertThat(providerRequest.getTemperature()).isEqualByComparingTo("0.70");
        assertThat(providerRequest.getMessages()).extracting(ChatRequest.Message::getRole)
                .containsExactly("system", "user");
        assertThat(providerRequest.getMessages()).extracting(ChatRequest.Message::getContent)
                .containsExactly("You are helpful.", "Hello");
    }

    @Test
    void streamMessageShouldEmitChunksAndStoreAccumulatedAssistantReply() {
        List<ChatStreamChunk> chunks = new ArrayList<>();
        List<ChatCompletionResponse> completions = new ArrayList<>();
        List<ChatSessionResponse> emittedSessions = new ArrayList<>();

        doAnswer(invocation -> {
            Consumer<ChatResponse> consumer = invocation.getArgument(2);
            consumer.accept(ChatResponse.delta("Hel"));
            consumer.accept(ChatResponse.delta("lo"));
            ChatResponse done = new ChatResponse();
            done.setId("stream-1");
            done.setModel("gpt-mock");
            done.setFinishReason("stop");
            ChatResponse.Usage usage = new ChatResponse.Usage();
            usage.setTotalTokens(9);
            done.setUsage(usage);
            consumer.accept(done);
            return null;
        }).when(providerService).streamChat(eq(10L), any(ChatRequest.class), any());

        chatService.streamMessage(request(null, "Hi"), new ChatStreamCallback() {
            @Override
            public void onSession(ChatSessionResponse session) {
                emittedSessions.add(session);
            }

            @Override
            public void onDelta(ChatStreamChunk chunk) {
                chunks.add(chunk);
            }

            @Override
            public void onComplete(ChatCompletionResponse response) {
                completions.add(response);
            }

            @Override
            public void onError(Throwable throwable) {
            }
        });

        assertThat(emittedSessions).hasSize(1);
        assertThat(chunks).extracting(ChatStreamChunk::getContent).containsExactly("Hel", "lo", null);
        assertThat(chunks.get(2).getDone()).isTrue();
        assertThat(completions).hasSize(1);
        assertThat(completions.get(0).getAssistantMessage().getContent()).isEqualTo("Hello");
        assertThat(completions.get(0).getAssistantMessage().getTokenCount()).isEqualTo(9);
        assertThat(messages).extracting(ChatMessagePo::getRole).containsExactly("user", "assistant");
    }

    @Test
    void streamMessageShouldExecuteWorkflowWhenRequestHasWorkflowId() {
        when(workflowRunService.execute(99L, "Hi")).thenReturn("workflow answer");
        List<ChatStreamChunk> chunks = new ArrayList<>();
        List<ChatCompletionResponse> completions = new ArrayList<>();

        chatService.streamMessage(workflowRequest(null, "Hi"), callback(chunks, completions));

        verify(workflowRunService).execute(99L, "Hi");
        verify(providerService, never()).streamChat(any(), any(), any());
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getDone()).isTrue();
        assertThat(completions).hasSize(1);
        assertThat(completions.get(0).getSession().getAgentId()).isNull();
        assertThat(completions.get(0).getSession().getWorkflowId()).isEqualTo(99L);
        assertThat(completions.get(0).getAssistantMessage().getContent()).isEqualTo("workflow answer");
        assertThat(messages).extracting(ChatMessagePo::getRole).containsExactly("user", "assistant");
    }

    @Test
    void streamMessageShouldPushErrorAndCompleteWhenWorkflowBizExceptionOccurs() {
        when(workflowRunService.execute(99L, "Hi"))
                .thenThrow(new BizException(ErrorCode.PARAM_ERROR, "工作流配置错误"));
        List<ChatStreamChunk> chunks = new ArrayList<>();
        List<ChatCompletionResponse> completions = new ArrayList<>();

        chatService.streamMessage(workflowRequest(null, "Hi"), callback(chunks, completions));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getContent()).isEqualTo("工作流执行失败：工作流配置错误");
        assertThat(chunks.get(0).getDone()).isFalse();
        assertThat(chunks.get(1).getDone()).isTrue();
        assertThat(completions).hasSize(1);
        assertThat(completions.get(0).getAssistantMessage().getContent()).isEqualTo("工作流执行失败：工作流配置错误");
        verify(providerService, never()).streamChat(any(), any(), any());
    }

    private void stubPersistence() {
        AtomicLong sessionId = new AtomicLong(1);
        AtomicLong messageId = new AtomicLong(100);
        doAnswer(invocation -> {
            ChatSessionPo session = invocation.getArgument(0);
            session.setId(sessionId.getAndIncrement());
            session.setCreatedAt(LocalDateTime.parse("2026-05-13T10:00:00"));
            session.setUpdatedAt(LocalDateTime.parse("2026-05-13T10:00:00"));
            sessions.add(session);
            return 1;
        }).when(sessionMapper).insert(any(ChatSessionPo.class));
        doAnswer(invocation -> {
            ChatSessionPo update = invocation.getArgument(0);
            sessions.stream()
                    .filter(session -> session.getId().equals(update.getId()))
                    .findFirst()
                    .ifPresent(session -> session.setUpdatedAt(LocalDateTime.parse("2026-05-13T10:00:01")));
            return 1;
        }).when(sessionMapper).updateById(any(ChatSessionPo.class));
        doAnswer(invocation -> {
            ChatMessagePo message = invocation.getArgument(0);
            message.setId(messageId.getAndIncrement());
            message.setCreatedAt(LocalDateTime.parse("2026-05-13T10:00:00"));
            messages.add(message);
            return 1;
        }).when(messageMapper).insert(any(ChatMessagePo.class));
        lenient().when(messageMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> new ArrayList<>(messages));
    }

    private ChatSendRequest request(Long sessionId, String content) {
        ChatSendRequest request = new ChatSendRequest();
        request.setAgentId(1L);
        request.setSessionId(sessionId);
        request.setUserId("u-1");
        request.setContent(content);
        return request;
    }

    private ChatSendRequest workflowRequest(Long sessionId, String content) {
        ChatSendRequest request = new ChatSendRequest();
        request.setWorkflowId(99L);
        request.setSessionId(sessionId);
        request.setUserId("u-1");
        request.setContent(content);
        return request;
    }

    private ChatStreamCallback callback(List<ChatStreamChunk> chunks, List<ChatCompletionResponse> completions) {
        return new ChatStreamCallback() {
            @Override
            public void onSession(ChatSessionResponse session) {
            }

            @Override
            public void onDelta(ChatStreamChunk chunk) {
                chunks.add(chunk);
            }

            @Override
            public void onComplete(ChatCompletionResponse response) {
                completions.add(response);
            }

            @Override
            public void onError(Throwable throwable) {
            }
        };
    }

    private AgentDetailResponse agent() {
        AgentDetailResponse agent = new AgentDetailResponse();
        agent.setId(1L);
        agent.setEnabled(1);
        agent.setModelConfigId(10L);
        agent.setSystemPrompt("You are helpful.");
        agent.setTemperature(new BigDecimal("0.70"));
        agent.setTopP(new BigDecimal("1.00"));
        agent.setMaxTokens(1024);
        agent.setMaxContextTurns(6);
        return agent;
    }

    private ModelConfigDto model() {
        ModelConfigDto model = new ModelConfigDto();
        model.setId(10L);
        model.setProviderId(20L);
        model.setModelId("gpt-mock");
        model.setEnabled(1);
        return model;
    }
}
