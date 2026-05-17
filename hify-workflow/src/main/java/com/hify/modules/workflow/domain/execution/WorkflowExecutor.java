package com.hify.modules.workflow.domain.execution;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.workflow.api.dto.WorkflowEdgeType;
import com.hify.modules.workflow.domain.NodeConfigParser;
import com.hify.modules.workflow.domain.handler.NodeHandlerRegistry;
import com.hify.modules.workflow.domain.handler.WorkflowNodeHandler;
import com.hify.modules.workflow.domain.model.NodeConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WorkflowExecutor {

    private static final int MAX_STEPS = 100;

    private final NodeConfigParser nodeConfigParser;
    private final NodeHandlerRegistry handlerRegistry;
    private final ConditionExpressionEvaluator expressionEvaluator;
    private final WorkflowRunRecorder recorder;

    public void run(WorkflowDefinition definition, WorkflowContext context) {
        String currentNodeId = definition.startNodeId();
        int steps = 0;
        while (currentNodeId != null) {
            if (++steps > MAX_STEPS) {
                throw new BizException(ErrorCode.PARAM_ERROR, "工作流执行超过最大节点数，可能存在循环");
            }
            WorkflowRuntimeNode node = definition.getNode(currentNodeId);
            Map<String, Object> inputSnapshot = context.snapshot();
            LocalDateTime startedAt = LocalDateTime.now();
            try {
                NodeConfig config = nodeConfigParser.parse(node.nodeType(), node.configJson());
                WorkflowNodeHandler handler = handlerRegistry.get(node.nodeType());
                NodeExecuteResult result = handler.execute(node, config, context);
                recorder.recordNode(context.getRunId(), context.getWorkflowId(), node,
                        "succeeded", inputSnapshot, result.output(), null, startedAt);
                if (result.stop()) {
                    return;
                }
                currentNodeId = selectNextNode(definition, node, context, result);
            } catch (RuntimeException exception) {
                recorder.recordNode(context.getRunId(), context.getWorkflowId(), node,
                        "failed", inputSnapshot, Map.of(), exception.getMessage(), startedAt);
                String errorNextNodeId = selectErrorNode(definition, node);
                if (errorNextNodeId == null) {
                    throw exception;
                }
                context.putNodeOutput(node.nodeId(), "errorMessage", exception.getMessage());
                currentNodeId = errorNextNodeId;
            }
        }
    }

    private String selectNextNode(WorkflowDefinition definition,
                                  WorkflowRuntimeNode node,
                                  WorkflowContext context,
                                  NodeExecuteResult result) {
        List<WorkflowRuntimeEdge> edges = definition.getOutgoingEdges(node.nodeId());
        for (WorkflowRuntimeEdge edge : edges) {
            if (edge.edgeType() == WorkflowEdgeType.ERROR) {
                continue;
            }
            if (edge.edgeType() == WorkflowEdgeType.NORMAL) {
                return edge.targetNodeId();
            }
            if (edge.edgeType() == WorkflowEdgeType.CONDITION
                    && expressionEvaluator.evaluate(edge.conditionExpression(), context)) {
                return edge.targetNodeId();
            }
        }
        return null;
    }

    private String selectErrorNode(WorkflowDefinition definition, WorkflowRuntimeNode node) {
        for (WorkflowRuntimeEdge edge : definition.getOutgoingEdges(node.nodeId())) {
            if (edge.edgeType() == WorkflowEdgeType.ERROR) {
                return edge.targetNodeId();
            }
        }
        return null;
    }
}
