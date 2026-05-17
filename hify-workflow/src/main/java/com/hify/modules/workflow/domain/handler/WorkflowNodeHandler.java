package com.hify.modules.workflow.domain.handler;

import com.hify.modules.workflow.api.dto.WorkflowNodeType;
import com.hify.modules.workflow.domain.execution.NodeExecuteResult;
import com.hify.modules.workflow.domain.execution.WorkflowContext;
import com.hify.modules.workflow.domain.execution.WorkflowRuntimeNode;
import com.hify.modules.workflow.domain.model.NodeConfig;

public interface WorkflowNodeHandler {

    WorkflowNodeType supportType();

    NodeExecuteResult execute(WorkflowRuntimeNode node, NodeConfig config, WorkflowContext context);
}
