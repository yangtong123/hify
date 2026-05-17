package com.hify.modules.workflow.domain.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.modules.knowledge.api.KnowledgeService;
import com.hify.modules.knowledge.api.dto.RetrievedChunkDto;
import com.hify.modules.provider.api.ProviderService;
import com.hify.modules.provider.api.dto.ChatRequest;
import com.hify.modules.provider.api.dto.ChatResponse;
import com.hify.modules.provider.api.dto.ModelConfigDto;
import com.hify.modules.workflow.api.dto.WorkflowEdgeType;
import com.hify.modules.workflow.api.dto.WorkflowNodeType;
import com.hify.modules.workflow.domain.NodeConfigParser;
import com.hify.modules.workflow.domain.handler.ConditionNodeHandler;
import com.hify.modules.workflow.domain.handler.EndNodeHandler;
import com.hify.modules.workflow.domain.handler.KnowledgeNodeHandler;
import com.hify.modules.workflow.domain.handler.LlmNodeHandler;
import com.hify.modules.workflow.domain.handler.NodeHandlerRegistry;
import com.hify.modules.workflow.domain.handler.StartNodeHandler;
import com.hify.modules.workflow.domain.handler.ToolNodeHandler;
import com.hify.modules.workflow.domain.tool.WorkflowToolRegistry;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowExecutorTest {

    @Mock
    private ProviderService providerService;

    @Mock
    private KnowledgeService knowledgeService;

    @Mock
    private WorkflowRunRecorder recorder;

    private ObjectMapper objectMapper;
    private WorkflowExecutor executor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        TemplateRenderer templateRenderer = new TemplateRenderer(objectMapper);
        ConditionExpressionEvaluator expressionEvaluator = new ConditionExpressionEvaluator();
        NodeHandlerRegistry registry = new NodeHandlerRegistry(List.of(
                new StartNodeHandler(),
                new KnowledgeNodeHandler(knowledgeService, templateRenderer),
                new LlmNodeHandler(providerService, templateRenderer),
                new ConditionNodeHandler(expressionEvaluator),
                new ToolNodeHandler(new WorkflowToolRegistry(List.of())),
                new EndNodeHandler(templateRenderer)
        ));
        executor = new WorkflowExecutor(new NodeConfigParser(objectMapper), registry, expressionEvaluator, recorder);
    }

    @Test
    void runShouldPassKnowledgeOutputIntoLlmAndProduceEndResponse() {
        RetrievedChunkDto chunk = new RetrievedChunkDto();
        chunk.setContent("七天内可退货");
        chunk.setSimilarity(0.91D);
        doReturn(List.of(chunk)).when(knowledgeService).retrieve(anyList(), anyString(), any(), any());

        ModelConfigDto model = new ModelConfigDto();
        model.setModelId("gpt-mock");
        when(providerService.getModelConfig(10L)).thenReturn(model);
        ChatResponse llmResponse = new ChatResponse();
        llmResponse.setContent("可以退货");
        when(providerService.chat(eq(10L), any(ChatRequest.class))).thenReturn(llmResponse);

        WorkflowDefinition definition = definition();
        WorkflowContext context = new WorkflowContext(100L, 1L, "u-1", Map.of("userMessage", "耳机能退吗"));

        executor.run(definition, context);

        assertThat(context.getOutputs())
                .containsEntry("retrieve_policy.chunks", List.of(chunk))
                .containsEntry("reply.answer", "可以退货")
                .containsEntry("end.response", "可以退货");

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(providerService).chat(eq(10L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getMessages()).extracting(ChatRequest.Message::getContent)
                .anyMatch(content -> content.contains("七天内可退货"));
        verify(knowledgeService).retrieve(eq(List.of(1L)), eq("耳机能退吗"), eq(3), any(BigDecimal.class));

        ArgumentCaptor<WorkflowRuntimeNode> nodeCaptor = ArgumentCaptor.forClass(WorkflowRuntimeNode.class);
        verify(recorder, org.mockito.Mockito.times(4)).recordNode(
                eq(100L), eq(1L), nodeCaptor.capture(), eq("succeeded"), any(), any(), eq(null), any(LocalDateTime.class));
        assertThat(nodeCaptor.getAllValues()).extracting(WorkflowRuntimeNode::nodeId)
                .containsExactly("start", "retrieve_policy", "reply", "end");
    }

    private WorkflowDefinition definition() {
        List<WorkflowRuntimeNode> nodes = new ArrayList<>();
        nodes.add(new WorkflowRuntimeNode("start", WorkflowNodeType.START, "Start", objectMapper.createObjectNode()));
        nodes.add(new WorkflowRuntimeNode("retrieve_policy", WorkflowNodeType.KNOWLEDGE, "Retrieve Policy",
                objectMapper.valueToTree(Map.of(
                        "knowledgeBaseIds", List.of(1L),
                        "queryTemplate", "{{inputs.userMessage}}",
                        "topK", 3,
                        "similarityThreshold", new BigDecimal("0.7000"),
                        "outputVariable", "chunks"
                ))));
        nodes.add(new WorkflowRuntimeNode("reply", WorkflowNodeType.LLM, "Reply",
                objectMapper.valueToTree(Map.of(
                        "modelConfigId", 10L,
                        "systemPrompt", "你是客服",
                        "userPromptTemplate", "用户问题：{{inputs.userMessage}}\n资料：{{retrieve_policy.chunks}}",
                        "temperature", new BigDecimal("0.30"),
                        "outputVariable", "answer"
                ))));
        nodes.add(new WorkflowRuntimeNode("end", WorkflowNodeType.END, "End",
                objectMapper.valueToTree(Map.of("responseTemplate", "{{reply.answer}}"))));

        List<WorkflowRuntimeEdge> edges = List.of(
                new WorkflowRuntimeEdge("start", "retrieve_policy", WorkflowEdgeType.NORMAL, null, 0),
                new WorkflowRuntimeEdge("retrieve_policy", "reply", WorkflowEdgeType.NORMAL, null, 0),
                new WorkflowRuntimeEdge("reply", "end", WorkflowEdgeType.NORMAL, null, 0)
        );
        return WorkflowDefinition.of(1L, 1, "start", nodes, edges);
    }
}
