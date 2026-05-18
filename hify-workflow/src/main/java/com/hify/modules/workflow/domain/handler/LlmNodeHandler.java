package com.hify.modules.workflow.domain.handler;

import com.hify.modules.provider.api.ProviderService;
import com.hify.modules.provider.api.dto.ChatRequest;
import com.hify.modules.provider.api.dto.ChatResponse;
import com.hify.modules.provider.api.dto.ModelConfigDto;
import com.hify.modules.workflow.api.dto.WorkflowNodeType;
import com.hify.modules.workflow.domain.execution.NodeExecuteResult;
import com.hify.modules.workflow.domain.execution.TemplateRenderer;
import com.hify.modules.workflow.domain.execution.WorkflowContext;
import com.hify.modules.workflow.domain.execution.WorkflowRuntimeNode;
import com.hify.modules.workflow.domain.model.LlmNodeConfig;
import com.hify.modules.workflow.domain.model.NodeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class LlmNodeHandler implements WorkflowNodeHandler {

    private final ProviderService providerService;
    private final TemplateRenderer templateRenderer;

    @Override
    public WorkflowNodeType supportType() {
        return WorkflowNodeType.LLM;
    }

    @Override
    public NodeExecuteResult execute(WorkflowRuntimeNode node, NodeConfig config, WorkflowContext context) {
        LlmNodeConfig llmConfig = (LlmNodeConfig) config;
        ModelConfigDto model = providerService.getModelConfig(llmConfig.modelConfigId());
        long start = System.currentTimeMillis();

        ChatRequest request = new ChatRequest();
        request.setModel(model.getModelId());
        request.setTemperature(llmConfig.temperature());
        List<ChatRequest.Message> messages = new ArrayList<>();
        if (StringUtils.hasText(llmConfig.systemPrompt())) {
            messages.add(message("system", templateRenderer.render(llmConfig.systemPrompt(), context)));
        }
        messages.add(message("user", templateRenderer.render(llmConfig.userPromptTemplate(), context)));
        request.setMessages(messages);
        log.info("Workflow LLM node started: workflowId={}, runId={}, nodeId={}, modelConfigId={}, model={}, messages={}",
                context.getWorkflowId(), context.getRunId(), node.nodeId(), llmConfig.modelConfigId(),
                model.getModelId(), messages.size());

        try {
            ChatResponse response = providerService.chat(llmConfig.modelConfigId(), request);
            String outputVariable = StringUtils.hasText(llmConfig.outputVariable()) ? llmConfig.outputVariable() : "text";
            context.putNodeOutput(node.nodeId(), outputVariable, response.getContent());
            log.info("Workflow LLM node completed: workflowId={}, runId={}, nodeId={}, model={}, latency={}ms, tokens={}",
                    context.getWorkflowId(), context.getRunId(), node.nodeId(), response.getModel(),
                    System.currentTimeMillis() - start, totalTokens(response));
            return NodeExecuteResult.success(Map.of(outputVariable, response.getContent()));
        } catch (RuntimeException e) {
            log.warn("Workflow LLM node failed: workflowId={}, runId={}, nodeId={}, modelConfigId={}, latency={}ms, error={}",
                    context.getWorkflowId(), context.getRunId(), node.nodeId(), llmConfig.modelConfigId(),
                    System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
    }

    private ChatRequest.Message message(String role, String content) {
        ChatRequest.Message message = new ChatRequest.Message();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private Integer totalTokens(ChatResponse response) {
        return response != null && response.getUsage() != null ? response.getUsage().getTotalTokens() : null;
    }
}
