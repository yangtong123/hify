package com.hify.modules.workflow.domain.handler;

import com.hify.modules.workflow.api.dto.WorkflowNodeType;
import com.hify.modules.workflow.domain.execution.NodeExecuteResult;
import com.hify.modules.workflow.domain.execution.WorkflowContext;
import com.hify.modules.workflow.domain.execution.WorkflowRuntimeNode;
import com.hify.modules.workflow.domain.model.NodeConfig;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StartNodeHandler implements WorkflowNodeHandler {

    @Override
    public WorkflowNodeType supportType() {
        return WorkflowNodeType.START;
    }

    @Override
    public NodeExecuteResult execute(WorkflowRuntimeNode node, NodeConfig config, WorkflowContext context) {
        return NodeExecuteResult.success(Map.of("inputs", context.getInputs()));
    }
}
