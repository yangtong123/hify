package com.hify.modules.workflow.domain.handler;

import com.hify.modules.workflow.api.dto.WorkflowNodeType;
import com.hify.modules.workflow.domain.execution.NodeExecuteResult;
import com.hify.modules.workflow.domain.execution.WorkflowContext;
import com.hify.modules.workflow.domain.execution.WorkflowRuntimeNode;
import com.hify.modules.workflow.domain.model.NodeConfig;
import com.hify.modules.workflow.domain.model.ToolNodeConfig;
import com.hify.modules.workflow.domain.tool.WorkflowToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ToolNodeHandler implements WorkflowNodeHandler {

    private final WorkflowToolRegistry toolRegistry;

    @Override
    public WorkflowNodeType supportType() {
        return WorkflowNodeType.TOOL;
    }

    @Override
    public NodeExecuteResult execute(WorkflowRuntimeNode node, NodeConfig config, WorkflowContext context) {
        ToolNodeConfig toolConfig = (ToolNodeConfig) config;
        Map<String, Object> inputs = resolveInputs(toolConfig.inputMapping(), context);
        long start = System.currentTimeMillis();
        log.info("Workflow tool node started: workflowId={}, runId={}, nodeId={}, toolCode={}, inputs={}",
                context.getWorkflowId(), context.getRunId(), node.nodeId(), toolConfig.toolCode(), inputs.size());
        try {
            Object result = toolRegistry.get(toolConfig.toolCode()).invoke(inputs, context);
            String outputVariable = StringUtils.hasText(toolConfig.outputVariable()) ? toolConfig.outputVariable() : "result";
            context.putNodeOutput(node.nodeId(), outputVariable, result);
            log.info("Workflow tool node completed: workflowId={}, runId={}, nodeId={}, toolCode={}, latency={}ms",
                    context.getWorkflowId(), context.getRunId(), node.nodeId(), toolConfig.toolCode(),
                    System.currentTimeMillis() - start);
            return NodeExecuteResult.success(Map.of(outputVariable, result));
        } catch (RuntimeException e) {
            log.warn("Workflow tool node failed: workflowId={}, runId={}, nodeId={}, toolCode={}, latency={}ms, error={}",
                    context.getWorkflowId(), context.getRunId(), node.nodeId(), toolConfig.toolCode(),
                    System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
    }

    private Map<String, Object> resolveInputs(Map<String, String> inputMapping, WorkflowContext context) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        if (inputMapping == null || inputMapping.isEmpty()) {
            return inputs;
        }
        for (Map.Entry<String, String> entry : inputMapping.entrySet()) {
            inputs.put(entry.getKey(), context.resolve(entry.getValue()));
        }
        return inputs;
    }
}
