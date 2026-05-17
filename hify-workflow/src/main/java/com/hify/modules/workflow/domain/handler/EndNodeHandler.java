package com.hify.modules.workflow.domain.handler;

import com.hify.modules.workflow.api.dto.WorkflowNodeType;
import com.hify.modules.workflow.domain.execution.NodeExecuteResult;
import com.hify.modules.workflow.domain.execution.TemplateRenderer;
import com.hify.modules.workflow.domain.execution.WorkflowContext;
import com.hify.modules.workflow.domain.execution.WorkflowRuntimeNode;
import com.hify.modules.workflow.domain.model.EndNodeConfig;
import com.hify.modules.workflow.domain.model.NodeConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class EndNodeHandler implements WorkflowNodeHandler {

    private final TemplateRenderer templateRenderer;

    @Override
    public WorkflowNodeType supportType() {
        return WorkflowNodeType.END;
    }

    @Override
    public NodeExecuteResult execute(WorkflowRuntimeNode node, NodeConfig config, WorkflowContext context) {
        EndNodeConfig endConfig = (EndNodeConfig) config;
        String response = templateRenderer.render(endConfig.responseTemplate(), context);
        context.putNodeOutput(node.nodeId(), "response", response);
        return NodeExecuteResult.stop(Map.of("response", response));
    }
}
