package com.hify.modules.workflow.domain.handler;

import com.hify.modules.knowledge.api.KnowledgeService;
import com.hify.modules.knowledge.api.dto.RetrievedChunkDto;
import com.hify.modules.workflow.api.dto.WorkflowNodeType;
import com.hify.modules.workflow.domain.execution.NodeExecuteResult;
import com.hify.modules.workflow.domain.execution.TemplateRenderer;
import com.hify.modules.workflow.domain.execution.WorkflowContext;
import com.hify.modules.workflow.domain.execution.WorkflowRuntimeNode;
import com.hify.modules.workflow.domain.model.KnowledgeNodeConfig;
import com.hify.modules.workflow.domain.model.NodeConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class KnowledgeNodeHandler implements WorkflowNodeHandler {

    private final KnowledgeService knowledgeService;
    private final TemplateRenderer templateRenderer;

    @Override
    public WorkflowNodeType supportType() {
        return WorkflowNodeType.KNOWLEDGE;
    }

    @Override
    public NodeExecuteResult execute(WorkflowRuntimeNode node, NodeConfig config, WorkflowContext context) {
        KnowledgeNodeConfig knowledgeConfig = (KnowledgeNodeConfig) config;
        String query = templateRenderer.render(knowledgeConfig.queryTemplate(), context);
        List<RetrievedChunkDto> chunks = knowledgeService.retrieve(
                knowledgeConfig.knowledgeBaseIds(),
                query,
                knowledgeConfig.topK(),
                knowledgeConfig.similarityThreshold());
        String outputVariable = StringUtils.hasText(knowledgeConfig.outputVariable())
                ? knowledgeConfig.outputVariable()
                : "chunks";
        context.putNodeOutput(node.nodeId(), outputVariable, chunks);
        return NodeExecuteResult.success(Map.of(outputVariable, chunks));
    }
}
