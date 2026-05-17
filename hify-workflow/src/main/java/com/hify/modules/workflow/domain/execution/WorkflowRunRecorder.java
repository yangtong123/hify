package com.hify.modules.workflow.domain.execution;

import com.hify.modules.workflow.infra.mapper.WorkflowNodeRunMapper;
import com.hify.modules.workflow.infra.mapper.WorkflowRunMapper;
import com.hify.modules.workflow.infra.po.WorkflowNodeRunPo;
import com.hify.modules.workflow.infra.po.WorkflowRunPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WorkflowRunRecorder {

    private final WorkflowRunMapper workflowRunMapper;
    private final WorkflowNodeRunMapper workflowNodeRunMapper;

    public WorkflowRunPo createRun(Long workflowId, Integer workflowVersion, String userId, Map<String, Object> inputs) {
        WorkflowRunPo run = new WorkflowRunPo();
        run.setWorkflowId(workflowId);
        run.setWorkflowVersion(workflowVersion);
        run.setUserId(userId);
        run.setStatus(WorkflowRunStatus.RUNNING.name().toLowerCase());
        run.setInputsJson(inputs);
        run.setOutputsJson(Map.of());
        run.setStartedAt(LocalDateTime.now());
        workflowRunMapper.insert(run);
        return run;
    }

    public void markRunSucceeded(Long runId, Map<String, Object> outputs, LocalDateTime startedAt) {
        WorkflowRunPo update = new WorkflowRunPo();
        update.setId(runId);
        update.setStatus(WorkflowRunStatus.SUCCEEDED.name().toLowerCase());
        update.setOutputsJson(outputs);
        LocalDateTime finishedAt = LocalDateTime.now();
        update.setFinishedAt(finishedAt);
        update.setElapsedMs(elapsedMs(startedAt, finishedAt));
        update.setUpdatedAt(null);
        workflowRunMapper.updateById(update);
    }

    public void markRunFailed(Long runId, Map<String, Object> outputs, String errorMessage, LocalDateTime startedAt) {
        WorkflowRunPo update = new WorkflowRunPo();
        update.setId(runId);
        update.setStatus(WorkflowRunStatus.FAILED.name().toLowerCase());
        update.setOutputsJson(outputs);
        update.setErrorMessage(limit(errorMessage));
        LocalDateTime finishedAt = LocalDateTime.now();
        update.setFinishedAt(finishedAt);
        update.setElapsedMs(elapsedMs(startedAt, finishedAt));
        update.setUpdatedAt(null);
        workflowRunMapper.updateById(update);
    }

    public void recordNode(Long runId,
                           Long workflowId,
                           WorkflowRuntimeNode node,
                           String status,
                           Map<String, Object> input,
                           Map<String, Object> output,
                           String errorMessage,
                           LocalDateTime startedAt) {
        LocalDateTime finishedAt = LocalDateTime.now();
        WorkflowNodeRunPo nodeRun = new WorkflowNodeRunPo();
        nodeRun.setWorkflowRunId(runId);
        nodeRun.setWorkflowId(workflowId);
        nodeRun.setNodeId(node.nodeId());
        nodeRun.setNodeType(node.nodeType().value());
        nodeRun.setStatus(status);
        nodeRun.setInputJson(input);
        nodeRun.setOutputJson(output);
        nodeRun.setErrorMessage(limit(errorMessage));
        nodeRun.setStartedAt(startedAt);
        nodeRun.setFinishedAt(finishedAt);
        nodeRun.setElapsedMs(elapsedMs(startedAt, finishedAt));
        workflowNodeRunMapper.insert(nodeRun);
    }

    private Long elapsedMs(LocalDateTime startedAt, LocalDateTime finishedAt) {
        if (startedAt == null || finishedAt == null) {
            return null;
        }
        return java.time.Duration.between(startedAt, finishedAt).toMillis();
    }

    private String limit(String value) {
        if (value == null || value.length() <= 1000) {
            return value;
        }
        return value.substring(0, 1000);
    }
}
