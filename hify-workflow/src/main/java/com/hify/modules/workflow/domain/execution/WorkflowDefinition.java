package com.hify.modules.workflow.domain.execution;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record WorkflowDefinition(
        Long workflowId,
        Integer version,
        String startNodeId,
        Map<String, WorkflowRuntimeNode> nodeMap,
        Map<String, List<WorkflowRuntimeEdge>> outgoingEdges
) {

    public WorkflowRuntimeNode getNode(String nodeId) {
        WorkflowRuntimeNode node = nodeMap.get(nodeId);
        if (node == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工作流节点不存在: " + nodeId);
        }
        return node;
    }

    public List<WorkflowRuntimeEdge> getOutgoingEdges(String nodeId) {
        return outgoingEdges.getOrDefault(nodeId, List.of());
    }

    public static WorkflowDefinition of(Long workflowId,
                                        Integer version,
                                        String startNodeId,
                                        List<WorkflowRuntimeNode> nodes,
                                        List<WorkflowRuntimeEdge> edges) {
        Map<String, WorkflowRuntimeNode> nodeMap = nodes.stream()
                .collect(Collectors.toMap(WorkflowRuntimeNode::nodeId, item -> item));
        Map<String, List<WorkflowRuntimeEdge>> outgoingEdges = edges.stream()
                .sorted(Comparator.comparing(edge -> edge.priority() != null ? edge.priority() : 0))
                .collect(Collectors.groupingBy(WorkflowRuntimeEdge::sourceNodeId));
        return new WorkflowDefinition(workflowId, version, startNodeId, nodeMap, outgoingEdges);
    }
}
