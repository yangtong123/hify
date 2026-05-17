package com.hify.modules.chat.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.exception.LlmApiException;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.modules.agent.api.AgentService;
import com.hify.modules.agent.api.dto.AgentDetailResponse;
import com.hify.modules.chat.api.ChatService;
import com.hify.modules.chat.api.ChatStreamCallback;
import com.hify.modules.chat.api.dto.ChatCompletionResponse;
import com.hify.modules.chat.api.dto.ChatMessageQuery;
import com.hify.modules.chat.api.dto.ChatMessageResponse;
import com.hify.modules.chat.api.dto.ChatSendRequest;
import com.hify.modules.chat.api.dto.ChatSessionQuery;
import com.hify.modules.chat.api.dto.ChatSessionResponse;
import com.hify.modules.chat.api.dto.ChatStreamChunk;
import com.hify.modules.chat.infra.mapper.ChatMessageMapper;
import com.hify.modules.chat.infra.mapper.ChatSessionMapper;
import com.hify.modules.chat.infra.po.ChatMessagePo;
import com.hify.modules.chat.infra.po.ChatSessionPo;
import com.hify.modules.provider.api.ProviderService;
import com.hify.modules.provider.api.dto.ChatRequest;
import com.hify.modules.provider.api.dto.ModelConfigDto;
import com.hify.modules.knowledge.api.KnowledgeService;
import com.hify.modules.knowledge.api.dto.RetrievedChunkDto;
import com.hify.modules.workflow.api.WorkflowRunService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_ARCHIVED = "archived";
    private static final int TITLE_MAX_LENGTH = 60;
    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final AgentService agentService;
    private final ProviderService providerService;
    private final KnowledgeService knowledgeService;
    private final WorkflowRunService workflowRunService;
    private final Executor llmExecutor;
    private final TransactionTemplate transactionTemplate;

    public ChatServiceImpl(ChatSessionMapper sessionMapper,
                           ChatMessageMapper messageMapper,
                           AgentService agentService,
                           ProviderService providerService,
                           KnowledgeService knowledgeService,
                           WorkflowRunService workflowRunService,
                           @Qualifier("llmExecutor") Executor llmExecutor,
                           TransactionTemplate transactionTemplate) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.agentService = agentService;
        this.providerService = providerService;
        this.knowledgeService = knowledgeService;
        this.workflowRunService = workflowRunService;
        this.llmExecutor = llmExecutor;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public ChatCompletionResponse completeMessage(ChatSendRequest request) {
        log.info("Chat completion started: userId={}, agentId={}, workflowId={}, sessionId={}, contentLength={}",
                request != null ? request.getUserId() : null,
                request != null ? request.getAgentId() : null,
                request != null ? request.getWorkflowId() : null,
                request != null ? request.getSessionId() : null,
                request != null ? lengthOf(request.getContent()) : 0);
        ResolvedChat resolved = resolveChat(request);
        ChatMessagePo userMessage = saveUserMessage(resolved.session().getId(), request.getContent());
        log.info("Chat user message saved: sessionId={}, messageId={}, agentId={}, workflowId={}",
                resolved.session().getId(), userMessage.getId(), agentIdOf(resolved), resolved.workflowId());

        if (resolved.workflowId() != null) {
            ChatMessagePo assistantMessage = executeWorkflowMessage(resolved, request.getContent(), null);
            return toCompletionResponse(resolved.session(), userMessage, assistantMessage);
        }

        ChatRequest providerRequest = buildProviderRequest(resolved, request.getContent());
        long start = System.currentTimeMillis();
        com.hify.modules.provider.api.dto.ChatResponse providerResponse;
        try {
            providerResponse = providerService.chat(resolved.agent().getModelConfigId(), providerRequest);
        } catch (RuntimeException e) {
            log.warn("Chat completion failed: sessionId={}, agentId={}, modelConfigId={}, latency={}ms, error={}",
                    resolved.session().getId(), resolved.agent().getId(), resolved.agent().getModelConfigId(),
                    System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
        long latencyMs = System.currentTimeMillis() - start;

        ChatMessagePo assistantMessage = saveAssistantMessage(resolved.session().getId(), providerResponse, latencyMs);
        log.info("Chat assistant message saved: sessionId={}, messageId={}, agentId={}",
                resolved.session().getId(), assistantMessage.getId(), resolved.agent().getId());
        log.info("Chat completed: sessionId={}, agentId={}, model={}, latency={}ms, tokens={}",
                resolved.session().getId(), resolved.agent().getId(), providerResponse.getModel(),
                latencyMs, totalTokens(providerResponse));
        return toCompletionResponse(resolved.session(), userMessage, assistantMessage);
    }

    @Override
    public SseEmitter sendMessage(ChatSendRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean closed = new AtomicBoolean(false);
        log.info("Chat SSE request accepted: userId={}, agentId={}, workflowId={}, sessionId={}, timeoutMs={}, contentLength={}",
                request != null ? request.getUserId() : null,
                request != null ? request.getAgentId() : null,
                request != null ? request.getWorkflowId() : null,
                request != null ? request.getSessionId() : null,
                SSE_TIMEOUT_MS,
                request != null ? lengthOf(request.getContent()) : 0);

        emitter.onTimeout(() -> onTimeout(emitter, closed));
        emitter.onCompletion(() -> closed.set(true));
        emitter.onError(throwable -> {
            closed.set(true);
            log.warn("Chat SSE emitter error: {}", throwable.getMessage());
        });

        llmExecutor.execute(() -> {
            try {
                streamMessage(request, new EmitterChatStreamCallback(emitter, closed));
            } catch (ClientDisconnectedException e) {
                closed.set(true);
                log.info("Chat SSE client disconnected, stopping LLM stream: {}", e.getMessage());
            } catch (LlmApiException e) {
                if (e.getErrorType() == LlmApiException.ErrorType.TIMEOUT) {
                    onTimeout(emitter, closed);
                } else {
                    completeWithError(emitter, closed, e);
                }
            } catch (SseSendException e) {
                completeWithError(emitter, closed, e);
            } catch (RuntimeException e) {
                completeWithError(emitter, closed, e);
            }
        });

        return emitter;
    }

    @Override
    public void streamMessage(ChatSendRequest request, ChatStreamCallback callback) {
        log.info("Chat stream started: userId={}, agentId={}, workflowId={}, sessionId={}, contentLength={}",
                request != null ? request.getUserId() : null,
                request != null ? request.getAgentId() : null,
                request != null ? request.getWorkflowId() : null,
                request != null ? request.getSessionId() : null,
                request != null ? lengthOf(request.getContent()) : 0);
        ResolvedChat resolved = resolveChat(request);
        ChatMessagePo userMessage = saveUserMessage(resolved.session().getId(), request.getContent());
        log.info("Chat stream user message saved: sessionId={}, messageId={}, agentId={}, workflowId={}",
                resolved.session().getId(), userMessage.getId(), agentIdOf(resolved), resolved.workflowId());
        callback.onSession(toSessionResponse(resolved.session()));

        if (resolved.workflowId() != null) {
            streamWorkflowMessage(resolved, userMessage, request.getContent(), callback);
            return;
        }

        ChatRequest providerRequest = buildProviderRequest(resolved, request.getContent());
        StreamingState state = new StreamingState();
        long start = System.currentTimeMillis();
        try {
            providerService.streamChat(resolved.agent().getModelConfigId(), providerRequest, chunk -> {
                state.apply(chunk);
                if (StringUtils.hasText(chunk.getContent())) {
                    ChatStreamChunk event = new ChatStreamChunk();
                    event.setSessionId(resolved.session().getId());
                    event.setContent(chunk.getContent());
                    event.setFinishReason(chunk.getFinishReason());
                    event.setDone(false);
                    callback.onDelta(event);
                }
            });

            long latencyMs = System.currentTimeMillis() - start;
            ChatMessagePo assistantMessage = saveMessage(
                    resolved.session().getId(),
                    "assistant",
                    state.content(),
                    state.tokenCount(),
                    state.toolCalls(),
                    state.metadata(latencyMs));
            log.info("Chat stream assistant message saved: sessionId={}, messageId={}, agentId={}",
                    resolved.session().getId(), assistantMessage.getId(), resolved.agent().getId());

            ChatStreamChunk done = new ChatStreamChunk();
            done.setSessionId(resolved.session().getId());
            done.setFinishReason(state.finishReason());
            done.setDone(true);
            callback.onDelta(done);
            callback.onComplete(toCompletionResponse(resolved.session(), userMessage, assistantMessage));
            log.info("Chat stream completed: sessionId={}, agentId={}, model={}, latency={}ms, tokens={}",
                    resolved.session().getId(), resolved.agent().getId(), state.model(), latencyMs, state.tokenCount());
        } catch (RuntimeException e) {
            if (e instanceof ClientDisconnectedException
                    || e instanceof SseSendException
                    || e instanceof LlmApiException) {
                throw e;
            }
            log.warn("Chat stream failed: sessionId={}, agentId={}, modelConfigId={}, error={}",
                    resolved.session().getId(), resolved.agent().getId(), resolved.agent().getModelConfigId(),
                    e.getMessage());
            callback.onError(e);
            throw e;
        }
    }

    private void streamWorkflowMessage(ResolvedChat resolved,
                                       ChatMessagePo userMessage,
                                       String userContent,
                                       ChatStreamCallback callback) {
        long start = System.currentTimeMillis();
        try {
            ChatMessagePo assistantMessage = executeWorkflowMessage(resolved, userContent, start);
            long latencyMs = System.currentTimeMillis() - start;
            sendDoneChunk(resolved.session().getId(), callback);
            callback.onComplete(toCompletionResponse(resolved.session(), userMessage, assistantMessage));
            log.info("Chat workflow stream completed: sessionId={}, agentId={}, workflowId={}, latency={}ms",
                    resolved.session().getId(), agentIdOf(resolved), resolved.workflowId(), latencyMs);
        } catch (BizException e) {
            long latencyMs = System.currentTimeMillis() - start;
            String errorMessage = "工作流执行失败：" + e.getMessage();
            ChatMessagePo assistantMessage = saveMessage(
                    resolved.session().getId(),
                    "assistant",
                    errorMessage,
                    null,
                    null,
                    workflowMetadata(resolved.workflowId(), latencyMs, e.getMessage()));
            log.warn("Chat workflow stream failed: sessionId={}, agentId={}, workflowId={}, error={}",
                    resolved.session().getId(), agentIdOf(resolved), resolved.workflowId(), e.getMessage());

            ChatStreamChunk error = new ChatStreamChunk();
            error.setSessionId(resolved.session().getId());
            error.setContent(errorMessage);
            error.setDone(false);
            callback.onDelta(error);
            sendDoneChunk(resolved.session().getId(), callback);
            callback.onComplete(toCompletionResponse(resolved.session(), userMessage, assistantMessage));
        }
    }

    private ChatMessagePo executeWorkflowMessage(ResolvedChat resolved, String userContent, Long startAt) {
        long start = startAt != null ? startAt : System.currentTimeMillis();
        String workflowResult = workflowRunService.execute(resolved.workflowId(), userContent);
        long latencyMs = System.currentTimeMillis() - start;
        ChatMessagePo assistantMessage = saveMessage(
                resolved.session().getId(),
                "assistant",
                workflowResult,
                null,
                null,
                workflowMetadata(resolved.workflowId(), latencyMs, null));
        log.info("Chat workflow assistant message saved: sessionId={}, messageId={}, agentId={}, workflowId={}",
                resolved.session().getId(), assistantMessage.getId(), agentIdOf(resolved), resolved.workflowId());
        return assistantMessage;
    }

    private void sendDoneChunk(Long sessionId, ChatStreamCallback callback) {
        ChatStreamChunk done = new ChatStreamChunk();
        done.setSessionId(sessionId);
        done.setFinishReason("stop");
        done.setDone(true);
        callback.onDelta(done);
    }

    @Override
    public PageResult<List<ChatSessionResponse>> listSessions(ChatSessionQuery query) {
        LambdaQueryWrapper<ChatSessionPo> wrapper = new LambdaQueryWrapper<ChatSessionPo>()
                .eq(ChatSessionPo::getUserId, query.getUserId())
                .eq(query.getAgentId() != null, ChatSessionPo::getAgentId, query.getAgentId())
                .eq(StringUtils.hasText(query.getStatus()), ChatSessionPo::getStatus, query.getStatus())
                .orderByDesc(ChatSessionPo::getUpdatedAt)
                .orderByDesc(ChatSessionPo::getId);

        Page<ChatSessionPo> page = PageHelper.toPage(query.getPage(), query.getSize());
        IPage<ChatSessionPo> result = sessionMapper.selectPage(page, wrapper);
        List<ChatSessionResponse> sessions = result.getRecords().stream()
                .map(this::toSessionResponse)
                .toList();
        return PageResult.ok(sessions, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    public ChatSessionResponse getSession(Long sessionId) {
        return toSessionResponse(requireSession(sessionId));
    }

    @Override
    public List<ChatMessageResponse> listMessages(Long sessionId, ChatMessageQuery query) {
        requireSession(sessionId);
        int size = normalizeSize(query.getSize());
        LambdaQueryWrapper<ChatMessagePo> wrapper = new LambdaQueryWrapper<ChatMessagePo>()
                .eq(ChatMessagePo::getSessionId, sessionId)
                .orderByDesc(ChatMessagePo::getCreatedAt)
                .orderByDesc(ChatMessagePo::getId)
                .last("LIMIT " + size);
        if (query.getBeforeCreatedAt() != null && query.getBeforeId() != null) {
            LocalDateTime beforeCreatedAt = query.getBeforeCreatedAt();
            Long beforeId = query.getBeforeId();
            wrapper.and(cursor -> cursor
                    .lt(ChatMessagePo::getCreatedAt, beforeCreatedAt)
                    .or(sameTime -> sameTime
                            .eq(ChatMessagePo::getCreatedAt, beforeCreatedAt)
                            .lt(ChatMessagePo::getId, beforeId)));
        }
        return messageMapper.selectList(wrapper).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Override
    @Transactional
    public void archiveSession(Long sessionId) {
        ChatSessionPo session = requireSession(sessionId);
        session.setStatus(STATUS_ARCHIVED);
        session.setUpdatedAt(null);
        sessionMapper.updateById(session);
        log.info("Chat session archived: sessionId={}, agentId={}, workflowId={}, userId={}",
                sessionId, session.getAgentId(), session.getWorkflowId(), session.getUserId());
    }

    private ResolvedChat resolveChat(ChatSendRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "聊天请求不能为空");
        }
        if (!StringUtils.hasText(request.getUserId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "用户标识不能为空");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "消息内容不能为空");
        }

        if (request.getSessionId() != null) {
            ChatSessionPo session = requireSession(request.getSessionId());
            if (!Objects.equals(session.getUserId(), request.getUserId())) {
                throw new BizException(ErrorCode.FORBIDDEN, "无权访问该会话");
            }
            validateSessionTarget(request, session);
            if (session.getWorkflowId() != null) {
                log.info("Chat resolved: sessionId={}, agentId={}, workflowId={}, modelConfigId={}, model={}, newSession={}",
                        session.getId(), null, session.getWorkflowId(), null, null, false);
                return new ResolvedChat(session, null, null, session.getWorkflowId());
            }
            AgentDetailResponse agent = requireEnabledAgent(session.getAgentId());
            ModelConfigDto model = providerService.getModelConfig(agent.getModelConfigId());
            Long workflowId = agent.getWorkflowId();
            log.info("Chat resolved: sessionId={}, agentId={}, workflowId={}, modelConfigId={}, model={}, newSession={}",
                    session.getId(), agent.getId(), workflowId, agent.getModelConfigId(), model.getModelId(), false);
            return new ResolvedChat(session, agent, model, workflowId);
        }

        boolean hasAgentTarget = request.getAgentId() != null;
        boolean hasWorkflowTarget = request.getWorkflowId() != null;
        if (hasAgentTarget == hasWorkflowTarget) {
            throw new BizException(ErrorCode.PARAM_ERROR, "新会话必须且只能指定 Agent ID 或工作流 ID");
        }
        if (hasWorkflowTarget) {
            ChatSessionPo session = createSession(null, request.getWorkflowId(), request.getUserId(), request.getContent());
            log.info("Chat resolved: sessionId={}, agentId={}, workflowId={}, modelConfigId={}, model={}, newSession={}",
                    session.getId(), null, request.getWorkflowId(), null, null, true);
            return new ResolvedChat(session, null, null, request.getWorkflowId());
        }

        AgentDetailResponse agent = requireEnabledAgent(request.getAgentId());
        ChatSessionPo session = createSession(agent.getId(), null, request.getUserId(), request.getContent());
        ModelConfigDto model = providerService.getModelConfig(agent.getModelConfigId());
        Long workflowId = agent.getWorkflowId();
        log.info("Chat resolved: sessionId={}, agentId={}, workflowId={}, modelConfigId={}, model={}, newSession={}",
                session.getId(), agent.getId(), workflowId, agent.getModelConfigId(), model.getModelId(), true);
        return new ResolvedChat(session, agent, model, workflowId);
    }

    private void validateSessionTarget(ChatSendRequest request, ChatSessionPo session) {
        if (request.getAgentId() != null && !Objects.equals(request.getAgentId(), session.getAgentId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "会话所属 Agent 与请求不一致");
        }
        if (request.getWorkflowId() != null && !Objects.equals(request.getWorkflowId(), session.getWorkflowId())) {
            Long agentWorkflowId = null;
            if (session.getAgentId() != null) {
                AgentDetailResponse sessionAgent = requireEnabledAgent(session.getAgentId());
                agentWorkflowId = sessionAgent.getWorkflowId();
            }
            if (!Objects.equals(request.getWorkflowId(), agentWorkflowId)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "会话所属工作流与请求不一致");
            }
        }
    }

    private AgentDetailResponse requireEnabledAgent(Long agentId) {
        if (agentId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "会话未关联 Agent");
        }
        AgentDetailResponse agent = agentService.getById(agentId);
        if (agent == null || !Objects.equals(agent.getEnabled(), 1)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "Agent 未启用: " + agentId);
        }
        return agent;
    }

    private ChatSessionPo createSession(Long agentId, Long workflowId, String userId, String content) {
        return executeInTransaction(() -> {
            ChatSessionPo session = new ChatSessionPo();
            session.setAgentId(agentId);
            session.setWorkflowId(workflowId);
            session.setUserId(userId);
            session.setTitle(generateTitle(content));
            session.setStatus(STATUS_ACTIVE);
            sessionMapper.insert(session);
            log.info("Chat session created: sessionId={}, agentId={}, workflowId={}, userId={}, titleLength={}",
                    session.getId(), agentId, workflowId, userId, lengthOf(session.getTitle()));
            return session;
        });
    }

    private ChatRequest buildProviderRequest(ResolvedChat resolved, String currentUserContent) {
        ChatRequest request = new ChatRequest();
        request.setModel(resolved.model().getModelId());
        request.setTemperature(resolved.agent().getTemperature());
        request.setTopP(resolved.agent().getTopP());
        request.setMaxTokens(resolved.agent().getMaxTokens());
        request.setMessages(buildMessages(resolved, currentUserContent));
        return request;
    }

    private List<ChatRequest.Message> buildMessages(ResolvedChat resolved, String currentUserContent) {
        List<ChatRequest.Message> messages = new ArrayList<>();
        if (StringUtils.hasText(resolved.agent().getSystemPrompt())) {
            messages.add(toProviderMessage("system", resolved.agent().getSystemPrompt()));
        }
        String knowledgeContext = buildKnowledgeContext(resolved.agent().getId(), currentUserContent);
        if (StringUtils.hasText(knowledgeContext)) {
            messages.add(toProviderMessage("system", knowledgeContext));
        }

        int maxTurns = resolved.agent().getMaxContextTurns() != null ? resolved.agent().getMaxContextTurns() : 10;
        int maxMessages = Math.max(maxTurns * 2, 1);
        List<ChatMessagePo> history = messageMapper.selectList(new LambdaQueryWrapper<ChatMessagePo>()
                .eq(ChatMessagePo::getSessionId, resolved.session().getId())
                .in(ChatMessagePo::getRole, List.of("user", "assistant", "tool"))
                .orderByDesc(ChatMessagePo::getCreatedAt)
                .orderByDesc(ChatMessagePo::getId)
                .last("LIMIT " + maxMessages));
        Collections.reverse(history);
        for (ChatMessagePo item : history) {
            messages.add(toProviderMessage(item.getRole(), item.getContent()));
        }
        if (history.stream().noneMatch(item -> Objects.equals(item.getRole(), "user")
                && Objects.equals(item.getContent(), currentUserContent))) {
            messages.add(toProviderMessage("user", currentUserContent));
        }
        log.info("Chat provider messages built: sessionId={}, agentId={}, historyMessages={}, totalMessages={}, hasKnowledgeContext={}",
                resolved.session().getId(), resolved.agent().getId(), history.size(), messages.size(),
                StringUtils.hasText(knowledgeContext));
        return messages;
    }

    private String buildKnowledgeContext(Long agentId, String currentUserContent) {
        List<RetrievedChunkDto> chunks = knowledgeService.retrieveForAgent(agentId, currentUserContent);
        if (chunks.isEmpty()) {
            log.info("Chat knowledge retrieval empty: agentId={}", agentId);
            return null;
        }
        log.info("Chat knowledge retrieval hit: agentId={}, chunks={}", agentId, chunks.size());
        StringBuilder builder = new StringBuilder();
        builder.append("请优先根据以下公司知识库资料回答用户问题。")
                .append("如果资料不足以确认答案，请明确说明无法从现有资料确认，不要编造公司政策。\n\n");
        int index = 1;
        for (RetrievedChunkDto chunk : chunks) {
            builder.append("[资料 ").append(index).append("]\n");
            builder.append("来源：");
            if (StringUtils.hasText(chunk.getDocumentTitle())) {
                builder.append(chunk.getDocumentTitle());
            } else if (StringUtils.hasText(chunk.getFileName())) {
                builder.append(chunk.getFileName());
            } else {
                builder.append("文档 ").append(chunk.getDocumentId());
            }
            if (chunk.getPageNumber() != null) {
                builder.append("，第 ").append(chunk.getPageNumber()).append(" 页");
            }
            if (StringUtils.hasText(chunk.getSectionTitle())) {
                builder.append("，").append(chunk.getSectionTitle());
            }
            builder.append("，chunkId=").append(chunk.getChunkId());
            if (chunk.getSimilarity() != null) {
                builder.append("，相似度=").append(String.format("%.4f", chunk.getSimilarity()));
            }
            builder.append("\n内容：").append(chunk.getContent()).append("\n\n");
            index++;
        }
        return builder.toString();
    }

    private ChatRequest.Message toProviderMessage(String role, String content) {
        ChatRequest.Message message = new ChatRequest.Message();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private ChatMessagePo saveUserMessage(Long sessionId, String content) {
        return saveMessage(sessionId, "user", content, null, null, null);
    }

    private ChatMessagePo saveAssistantMessage(Long sessionId,
                                               com.hify.modules.provider.api.dto.ChatResponse response,
                                               long latencyMs) {
        return saveMessage(
                sessionId,
                "assistant",
                response.getContent(),
                totalTokens(response),
                response.getToolCalls(),
                metadata(response, latencyMs));
    }

    private ChatMessagePo saveMessage(Long sessionId,
                                      String role,
                                      String content,
                                      Integer tokenCount,
                                      Object toolCalls,
                                      Map<String, Object> metadata) {
        return executeInTransaction(() -> {
            ChatMessagePo po = new ChatMessagePo();
            po.setSessionId(sessionId);
            po.setRole(role);
            po.setContent(content != null ? content : "");
            po.setTokenCount(tokenCount);
            po.setToolCalls(toolCalls);
            po.setMetadata(metadata);
            messageMapper.insert(po);
            touchSession(sessionId);
            return po;
        });
    }

    private int lengthOf(String value) {
        return value != null ? value.length() : 0;
    }

    private void touchSession(Long sessionId) {
        ChatSessionPo update = new ChatSessionPo();
        update.setId(sessionId);
        update.setUpdatedAt(null);
        sessionMapper.updateById(update);
    }

    private ChatSessionPo requireSession(Long sessionId) {
        ChatSessionPo session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        return session;
    }

    private Map<String, Object> metadata(com.hify.modules.provider.api.dto.ChatResponse response, long latencyMs) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("providerResponseId", response.getId());
        metadata.put("model", response.getModel());
        metadata.put("finishReason", response.getFinishReason());
        metadata.put("latencyMs", latencyMs);
        if (response.getUsage() != null) {
            metadata.put("promptTokens", response.getUsage().getPromptTokens());
            metadata.put("completionTokens", response.getUsage().getCompletionTokens());
            metadata.put("totalTokens", response.getUsage().getTotalTokens());
        }
        return metadata;
    }

    private Map<String, Object> workflowMetadata(Long workflowId, long latencyMs, String errorMessage) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("workflowId", workflowId);
        metadata.put("latencyMs", latencyMs);
        if (StringUtils.hasText(errorMessage)) {
            metadata.put("errorMessage", errorMessage);
        }
        return metadata;
    }

    private Integer totalTokens(com.hify.modules.provider.api.dto.ChatResponse response) {
        return response.getUsage() != null ? response.getUsage().getTotalTokens() : null;
    }

    private ChatCompletionResponse toCompletionResponse(ChatSessionPo session,
                                                        ChatMessagePo userMessage,
                                                        ChatMessagePo assistantMessage) {
        ChatCompletionResponse response = new ChatCompletionResponse();
        response.setSession(toSessionResponse(session));
        response.setUserMessage(toMessageResponse(userMessage));
        response.setAssistantMessage(toMessageResponse(assistantMessage));
        return response;
    }

    private ChatSessionResponse toSessionResponse(ChatSessionPo po) {
        ChatSessionResponse response = new ChatSessionResponse();
        response.setId(po.getId());
        response.setAgentId(po.getAgentId());
        response.setWorkflowId(po.getWorkflowId());
        response.setTitle(po.getTitle());
        response.setUserId(po.getUserId());
        response.setStatus(po.getStatus());
        response.setCreatedAt(po.getCreatedAt());
        response.setUpdatedAt(po.getUpdatedAt());
        return response;
    }

    private Long agentIdOf(ResolvedChat resolved) {
        return resolved.agent() != null ? resolved.agent().getId() : resolved.session().getAgentId();
    }

    private ChatMessageResponse toMessageResponse(ChatMessagePo po) {
        ChatMessageResponse response = new ChatMessageResponse();
        response.setId(po.getId());
        response.setSessionId(po.getSessionId());
        response.setRole(po.getRole());
        response.setContent(po.getContent());
        response.setTokenCount(po.getTokenCount());
        response.setToolCalls(po.getToolCalls());
        response.setMetadata(po.getMetadata());
        response.setCreatedAt(po.getCreatedAt());
        return response;
    }

    private String generateTitle(String content) {
        String title = content.trim().replaceAll("\\s+", " ");
        if (title.length() > TITLE_MAX_LENGTH) {
            return title.substring(0, TITLE_MAX_LENGTH);
        }
        return title;
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }

    private <T> T executeInTransaction(Supplier<T> supplier) {
        if (transactionTemplate == null) {
            return supplier.get();
        }
        return transactionTemplate.execute(status -> supplier.get());
    }

    private void onTimeout(SseEmitter emitter, AtomicBoolean closed) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        BizException timeout = new BizException(ErrorCode.THIRD_PARTY_ERROR, "LLM 响应超时");
        log.warn("Chat SSE timeout");
        emitter.completeWithError(timeout);
    }

    private void completeWithError(SseEmitter emitter, AtomicBoolean closed, Throwable throwable) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        log.warn("Chat SSE failed: {}", throwable.getMessage());
        emitter.completeWithError(throwable);
    }

    private static final class EmitterChatStreamCallback implements ChatStreamCallback {
        private final SseEmitter emitter;
        private final AtomicBoolean closed;

        private EmitterChatStreamCallback(SseEmitter emitter, AtomicBoolean closed) {
            this.emitter = emitter;
            this.closed = closed;
        }

        @Override
        public void onSession(ChatSessionResponse session) {
            send("session", session);
        }

        @Override
        public void onDelta(ChatStreamChunk chunk) {
            send("delta", chunk);
        }

        @Override
        public void onComplete(ChatCompletionResponse response) {
            send("complete", response);
            if (closed.compareAndSet(false, true)) {
                emitter.complete();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            throw new SseSendException("SSE 流处理失败", throwable);
        }

        private void send(String eventName, Object data) {
            if (closed.get()) {
                throw new ClientDisconnectedException("SSE 已关闭");
            }
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                closed.set(true);
                throw new ClientDisconnectedException("客户端已断开", e);
            } catch (RuntimeException e) {
                throw new SseSendException("SSE 发送失败", e);
            }
        }
    }

    private static final class ClientDisconnectedException extends RuntimeException {
        private ClientDisconnectedException(String message) {
            super(message);
        }

        private ClientDisconnectedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class SseSendException extends RuntimeException {
        private SseSendException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private record ResolvedChat(ChatSessionPo session, AgentDetailResponse agent, ModelConfigDto model, Long workflowId) {
    }

    private static final class StreamingState {
        private final StringBuilder content = new StringBuilder();
        private String id;
        private String model;
        private String finishReason;
        private Integer tokenCount;
        private Object toolCalls;

        private void apply(com.hify.modules.provider.api.dto.ChatResponse chunk) {
            if (chunk.getContent() != null) {
                content.append(chunk.getContent());
            }
            if (StringUtils.hasText(chunk.getId())) {
                id = chunk.getId();
            }
            if (StringUtils.hasText(chunk.getModel())) {
                model = chunk.getModel();
            }
            if (StringUtils.hasText(chunk.getFinishReason())) {
                finishReason = chunk.getFinishReason();
            }
            if (chunk.getUsage() != null) {
                tokenCount = chunk.getUsage().getTotalTokens();
            }
            if (chunk.getToolCalls() != null) {
                toolCalls = chunk.getToolCalls();
            }
        }

        private String content() {
            return content.toString();
        }

        private String model() {
            return model;
        }

        private String finishReason() {
            return finishReason;
        }

        private Integer tokenCount() {
            return tokenCount;
        }

        private Object toolCalls() {
            return toolCalls;
        }

        private Map<String, Object> metadata(long latencyMs) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("providerResponseId", id);
            metadata.put("model", model);
            metadata.put("finishReason", finishReason);
            metadata.put("latencyMs", latencyMs);
            metadata.put("totalTokens", tokenCount);
            return metadata;
        }
    }
}
