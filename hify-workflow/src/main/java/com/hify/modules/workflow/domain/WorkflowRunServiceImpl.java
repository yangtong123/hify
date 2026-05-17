package com.hify.modules.workflow.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.workflow.api.WorkflowRunService;
import com.hify.modules.workflow.api.dto.WorkflowEdgeType;
import com.hify.modules.workflow.api.dto.WorkflowNodeType;
import com.hify.modules.workflow.api.dto.run.WorkflowNodeRunResponse;
import com.hify.modules.workflow.api.dto.run.WorkflowRunDetailResponse;
import com.hify.modules.workflow.api.dto.run.WorkflowRunRequest;
import com.hify.modules.workflow.api.dto.run.WorkflowRunResponse;
import com.hify.modules.workflow.domain.execution.WorkflowContext;
import com.hify.modules.workflow.domain.execution.WorkflowDefinition;
import com.hify.modules.workflow.domain.execution.WorkflowExecutor;
import com.hify.modules.workflow.domain.execution.WorkflowRunRecorder;
import com.hify.modules.workflow.domain.execution.WorkflowRuntimeEdge;
import com.hify.modules.workflow.domain.execution.WorkflowRuntimeNode;
import com.hify.modules.workflow.infra.mapper.WorkflowEdgeMapper;
import com.hify.modules.workflow.infra.mapper.WorkflowMapper;
import com.hify.modules.workflow.infra.mapper.WorkflowNodeMapper;
import com.hify.modules.workflow.infra.mapper.WorkflowNodeRunMapper;
import com.hify.modules.workflow.infra.mapper.WorkflowRunMapper;
import com.hify.modules.workflow.infra.po.WorkflowEdgePo;
import com.hify.modules.workflow.infra.po.WorkflowNodePo;
import com.hify.modules.workflow.infra.po.WorkflowNodeRunPo;
import com.hify.modules.workflow.infra.po.WorkflowPo;
import com.hify.modules.workflow.infra.po.WorkflowRunPo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowRunServiceImpl implements WorkflowRunService {

    private final WorkflowMapper workflowMapper;
    private final WorkflowNodeMapper workflowNodeMapper;
    private final WorkflowEdgeMapper workflowEdgeMapper;
    private final WorkflowRunMapper workflowRunMapper;
    private final WorkflowNodeRunMapper workflowNodeRunMapper;
    private final WorkflowRunRecorder runRecorder;
    private final WorkflowExecutor workflowExecutor;

    @Override
    public String execute(Long workflowId, String userContent) {
        WorkflowRunRequest request = new WorkflowRunRequest();
        request.setUserId("chat");
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("userMessage", userContent);
        inputs.put("content", userContent);
        request.setInputs(inputs);

        WorkflowRunResponse response = run(workflowId, request);
        return extractTextOutput(response.getOutputs());
    }

    @Override
    public WorkflowRunResponse run(Long workflowId, WorkflowRunRequest request) {
        WorkflowPo workflow = getWorkflowOrThrow(workflowId);
        WorkflowDefinition definition = loadDefinition(workflow);
        Map<String, Object> inputs = request.getInputs() != null ? request.getInputs() : Map.of();
        WorkflowRunPo run = runRecorder.createRun(workflowId, workflow.getVersion(), request.getUserId(), inputs);
        WorkflowContext context = new WorkflowContext(run.getId(), workflowId, request.getUserId(), inputs);

        log.info("Workflow run started: runId={}, workflowId={}, userId={}",
                run.getId(), workflowId, request.getUserId());
        try {
            workflowExecutor.run(definition, context);
            runRecorder.markRunSucceeded(run.getId(), context.getOutputs(), run.getStartedAt());
            log.info("Workflow run succeeded: runId={}, workflowId={}, outputs={}",
                    run.getId(), workflowId, context.getOutputs().size());
        } catch (RuntimeException exception) {
            runRecorder.markRunFailed(run.getId(), context.getOutputs(), exception.getMessage(), run.getStartedAt());
            log.warn("Workflow run failed: runId={}, workflowId={}, error={}",
                    run.getId(), workflowId, exception.getMessage());
            throw exception;
        }
        return toRunResponse(workflowRunMapper.selectById(run.getId()));
    }

    @Override
    public WorkflowRunDetailResponse getRun(Long runId) {
        WorkflowRunPo run = workflowRunMapper.selectById(runId);
        if (run == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流执行记录不存在");
        }
        WorkflowRunDetailResponse response = new WorkflowRunDetailResponse();
        fillRunResponse(run, response);
        List<WorkflowNodeRunResponse> nodes = workflowNodeRunMapper.selectList(
                        new LambdaQueryWrapper<WorkflowNodeRunPo>()
                                .eq(WorkflowNodeRunPo::getWorkflowRunId, runId)
                                .orderByAsc(WorkflowNodeRunPo::getId))
                .stream()
                .map(this::toNodeRunResponse)
                .toList();
        response.setNodes(nodes);
        return response;
    }

    private WorkflowDefinition loadDefinition(WorkflowPo workflow) {
        List<WorkflowRuntimeNode> nodes = workflowNodeMapper.selectList(new LambdaQueryWrapper<WorkflowNodePo>()
                        .eq(WorkflowNodePo::getWorkflowId, workflow.getId())
                        .orderByAsc(WorkflowNodePo::getId))
                .stream()
                .map(node -> new WorkflowRuntimeNode(
                        node.getNodeId(),
                        WorkflowNodeType.from(node.getNodeType()),
                        node.getName(),
                        node.getConfigJson()))
                .toList();
        List<WorkflowRuntimeEdge> edges = workflowEdgeMapper.selectList(new LambdaQueryWrapper<WorkflowEdgePo>()
                        .eq(WorkflowEdgePo::getWorkflowId, workflow.getId())
                        .orderByAsc(WorkflowEdgePo::getPriority)
                        .orderByAsc(WorkflowEdgePo::getId))
                .stream()
                .map(edge -> new WorkflowRuntimeEdge(
                        edge.getSourceNodeId(),
                        edge.getTargetNodeId(),
                        WorkflowEdgeType.from(edge.getEdgeType()),
                        edge.getConditionExpression(),
                        edge.getPriority()))
                .toList();
        return WorkflowDefinition.of(workflow.getId(), workflow.getVersion(), workflow.getStartNodeId(), nodes, edges);
    }

    private String extractTextOutput(Map<String, Object> outputs) {
        if (outputs == null || outputs.isEmpty()) {
            return "";
        }
        Object response = outputs.get("end.response");
        if (response != null) {
            return Objects.toString(response, "");
        }
        for (Map.Entry<String, Object> entry : outputs.entrySet()) {
            if (StringUtils.endsWithIgnoreCase(entry.getKey(), ".response")) {
                return Objects.toString(entry.getValue(), "");
            }
        }
        for (String key : List.of("response", "answer", "text")) {
            Object value = outputs.get(key);
            if (value != null) {
                return Objects.toString(value, "");
            }
        }
        for (Map.Entry<String, Object> entry : outputs.entrySet()) {
            if (StringUtils.endsWithIgnoreCase(entry.getKey(), ".answer")
                    || StringUtils.endsWithIgnoreCase(entry.getKey(), ".text")) {
                return Objects.toString(entry.getValue(), "");
            }
        }
        return Objects.toString(outputs.values().iterator().next(), "");
    }

    private WorkflowPo getWorkflowOrThrow(Long workflowId) {
        WorkflowPo workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流不存在");
        }
        return workflow;
    }

    private WorkflowRunResponse toRunResponse(WorkflowRunPo run) {
        WorkflowRunResponse response = new WorkflowRunResponse();
        fillRunResponse(run, response);
        return response;
    }

    private void fillRunResponse(WorkflowRunPo run, WorkflowRunResponse response) {
        response.setId(run.getId());
        response.setWorkflowId(run.getWorkflowId());
        response.setStatus(run.getStatus());
        response.setOutputs(run.getOutputsJson());
        response.setErrorMessage(run.getErrorMessage());
        response.setStartedAt(run.getStartedAt());
        response.setFinishedAt(run.getFinishedAt());
        response.setElapsedMs(run.getElapsedMs());
    }

    private WorkflowNodeRunResponse toNodeRunResponse(WorkflowNodeRunPo run) {
        WorkflowNodeRunResponse response = new WorkflowNodeRunResponse();
        response.setId(run.getId());
        response.setWorkflowRunId(run.getWorkflowRunId());
        response.setNodeId(run.getNodeId());
        response.setNodeType(run.getNodeType());
        response.setStatus(run.getStatus());
        response.setInput(run.getInputJson());
        response.setOutput(run.getOutputJson());
        response.setErrorMessage(run.getErrorMessage());
        response.setStartedAt(run.getStartedAt());
        response.setFinishedAt(run.getFinishedAt());
        response.setElapsedMs(run.getElapsedMs());
        return response;
    }
}
