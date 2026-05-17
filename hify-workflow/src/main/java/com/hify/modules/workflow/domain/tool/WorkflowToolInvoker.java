package com.hify.modules.workflow.domain.tool;

import com.hify.modules.workflow.domain.execution.WorkflowContext;

import java.util.Map;

public interface WorkflowToolInvoker {

    String toolCode();

    Object invoke(Map<String, Object> inputs, WorkflowContext context);
}
