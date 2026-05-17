package com.hify.modules.workflow.domain.handler;

import com.hify.modules.workflow.api.dto.WorkflowNodeType;
import com.hify.modules.workflow.domain.execution.ConditionExpressionEvaluator;
import com.hify.modules.workflow.domain.execution.NodeExecuteResult;
import com.hify.modules.workflow.domain.execution.WorkflowContext;
import com.hify.modules.workflow.domain.execution.WorkflowRuntimeNode;
import com.hify.modules.workflow.domain.model.ConditionNodeConfig;
import com.hify.modules.workflow.domain.model.NodeConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ConditionNodeHandler implements WorkflowNodeHandler {

    private final ConditionExpressionEvaluator expressionEvaluator;

    @Override
    public WorkflowNodeType supportType() {
        return WorkflowNodeType.CONDITION;
    }

    @Override
    public NodeExecuteResult execute(WorkflowRuntimeNode node, NodeConfig config, WorkflowContext context) {
        ConditionNodeConfig conditionConfig = (ConditionNodeConfig) config;
        boolean matched = expressionEvaluator.evaluate(conditionConfig.expression(), context);
        context.putNodeOutput(node.nodeId(), "result", matched);
        return NodeExecuteResult.success(Map.of("result", matched));
    }
}
