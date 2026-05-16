package com.hify.modules.workflow.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.modules.workflow.api.WorkflowService;
import com.hify.modules.workflow.api.dto.WorkflowCreateRequest;
import com.hify.modules.workflow.api.dto.WorkflowDetailResponse;
import com.hify.modules.workflow.api.dto.WorkflowEdgeRequest;
import com.hify.modules.workflow.api.dto.WorkflowEdgeResponse;
import com.hify.modules.workflow.api.dto.WorkflowEdgeType;
import com.hify.modules.workflow.api.dto.WorkflowListResponse;
import com.hify.modules.workflow.api.dto.WorkflowNodeRequest;
import com.hify.modules.workflow.api.dto.WorkflowNodeResponse;
import com.hify.modules.workflow.api.dto.WorkflowNodeType;
import com.hify.modules.workflow.api.dto.WorkflowQuery;
import com.hify.modules.workflow.api.dto.WorkflowUpdateRequest;
import com.hify.modules.workflow.infra.mapper.WorkflowEdgeMapper;
import com.hify.modules.workflow.infra.mapper.WorkflowMapper;
import com.hify.modules.workflow.infra.mapper.WorkflowNodeMapper;
import com.hify.modules.workflow.infra.po.WorkflowEdgePo;
import com.hify.modules.workflow.infra.po.WorkflowNodePo;
import com.hify.modules.workflow.infra.po.WorkflowPo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private static final String DEFAULT_STATUS = "draft";
    private static final int DEFAULT_VERSION = 1;

    private final WorkflowMapper workflowMapper;
    private final WorkflowNodeMapper workflowNodeMapper;
    private final WorkflowEdgeMapper workflowEdgeMapper;
    private final NodeConfigParser nodeConfigParser;

    @Override
    @Transactional
    public WorkflowDetailResponse create(WorkflowCreateRequest request) {
        validateWorkflowDefinition(request.getStartNodeId(), request.getNodes(), request.getEdges());

        WorkflowPo workflow = new WorkflowPo();
        workflow.setName(request.getName());
        workflow.setDescription(defaultString(request.getDescription()));
        workflow.setStatus(defaultStatus(request.getStatus()));
        workflow.setVersion(defaultIfNull(request.getVersion(), DEFAULT_VERSION));
        workflow.setStartNodeId(request.getStartNodeId());
        workflow.setConfigJson(request.getConfig());
        workflowMapper.insert(workflow);

        insertNodes(workflow.getId(), request.getNodes());
        insertEdges(workflow.getId(), request.getEdges());

        log.info("Workflow created: id={}, name={}, nodes={}, edges={}",
                workflow.getId(), workflow.getName(), request.getNodes().size(), countList(request.getEdges()));
        return buildDetail(workflow);
    }

    @Override
    public PageResult<List<WorkflowListResponse>> list(WorkflowQuery query) {
        LambdaQueryWrapper<WorkflowPo> wrapper = new LambdaQueryWrapper<WorkflowPo>()
                .like(StringUtils.hasText(query.getName()), WorkflowPo::getName, query.getName())
                .eq(StringUtils.hasText(query.getStatus()), WorkflowPo::getStatus, query.getStatus())
                .orderByDesc(WorkflowPo::getCreatedAt);

        Page<WorkflowPo> page = PageHelper.toPage(query.getPage(), query.getSize());
        IPage<WorkflowPo> result = workflowMapper.selectPage(page, wrapper);
        List<WorkflowListResponse> list = result.getRecords().stream()
                .map(this::toListResponse)
                .toList();
        return PageResult.ok(list, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    public WorkflowDetailResponse getById(Long id) {
        WorkflowPo workflow = getWorkflowOrThrow(id);
        return buildDetail(workflow);
    }

    @Override
    @Transactional
    public WorkflowDetailResponse update(Long id, WorkflowUpdateRequest request) {
        WorkflowPo workflow = getWorkflowOrThrow(id);
        validateWorkflowDefinition(request.getStartNodeId(), request.getNodes(), request.getEdges());

        workflow.setName(request.getName());
        workflow.setDescription(defaultString(request.getDescription()));
        workflow.setStatus(defaultStatus(request.getStatus()));
        workflow.setVersion(defaultIfNull(request.getVersion(), workflow.getVersion()));
        workflow.setStartNodeId(request.getStartNodeId());
        workflow.setConfigJson(request.getConfig());
        workflow.setUpdatedAt(null);
        workflowMapper.updateById(workflow);

        deleteNodes(id);
        deleteEdges(id);
        insertNodes(id, request.getNodes());
        insertEdges(id, request.getEdges());

        log.info("Workflow updated: id={}, name={}, nodes={}, edges={}",
                id, workflow.getName(), request.getNodes().size(), countList(request.getEdges()));
        return buildDetail(getWorkflowOrThrow(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        WorkflowPo workflow = getWorkflowOrThrow(id);
        workflowMapper.deleteById(id);
        deleteNodes(id);
        deleteEdges(id);
        log.info("Workflow deleted: id={}, name={}", id, workflow.getName());
    }

    private void validateWorkflowDefinition(String startNodeId,
                                            List<WorkflowNodeRequest> nodes,
                                            List<WorkflowEdgeRequest> edges) {
        Set<String> nodeIds = new HashSet<>();
        for (WorkflowNodeRequest node : nodes) {
            if (!nodeIds.add(node.getNodeId())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "节点 ID 重复: " + node.getNodeId());
            }
            nodeConfigParser.parse(node.getNodeType(), node.getConfig());
        }
        if (!nodeIds.contains(startNodeId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "开始节点不存在: " + startNodeId);
        }

        for (WorkflowEdgeRequest edge : emptyIfNull(edges)) {
            if (!nodeIds.contains(edge.getSourceNodeId())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "连接起点节点不存在: " + edge.getSourceNodeId());
            }
            if (!nodeIds.contains(edge.getTargetNodeId())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "连接终点节点不存在: " + edge.getTargetNodeId());
            }
        }
    }

    private void insertNodes(Long workflowId, List<WorkflowNodeRequest> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<WorkflowNodePo> records = nodes.stream()
                .map(node -> toNodePo(workflowId, node, now))
                .toList();
        workflowNodeMapper.batchInsert(records);
    }

    private void insertEdges(Long workflowId, List<WorkflowEdgeRequest> edges) {
        if (edges == null || edges.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<WorkflowEdgePo> records = edges.stream()
                .map(edge -> toEdgePo(workflowId, edge, now))
                .toList();
        workflowEdgeMapper.batchInsert(records);
    }

    private WorkflowNodePo toNodePo(Long workflowId, WorkflowNodeRequest request, LocalDateTime now) {
        WorkflowNodePo po = new WorkflowNodePo();
        po.setWorkflowId(workflowId);
        po.setNodeId(request.getNodeId());
        po.setNodeType(request.getNodeType().value());
        po.setName(request.getName());
        po.setConfigJson(request.getConfig());
        po.setPositionJson(request.getPosition());
        po.setCreatedAt(now);
        po.setUpdatedAt(now);
        po.setDeleted(0);
        return po;
    }

    private WorkflowEdgePo toEdgePo(Long workflowId, WorkflowEdgeRequest request, LocalDateTime now) {
        WorkflowEdgePo po = new WorkflowEdgePo();
        po.setWorkflowId(workflowId);
        po.setSourceNodeId(request.getSourceNodeId());
        po.setTargetNodeId(request.getTargetNodeId());
        po.setEdgeType(defaultIfNull(request.getEdgeType(), WorkflowEdgeType.NORMAL).value());
        po.setConditionExpression(request.getConditionExpression());
        po.setPriority(defaultIfNull(request.getPriority(), 0));
        po.setCreatedAt(now);
        po.setUpdatedAt(now);
        po.setDeleted(0);
        return po;
    }

    private void deleteNodes(Long workflowId) {
        workflowNodeMapper.delete(new LambdaQueryWrapper<WorkflowNodePo>()
                .eq(WorkflowNodePo::getWorkflowId, workflowId));
    }

    private void deleteEdges(Long workflowId) {
        workflowEdgeMapper.delete(new LambdaQueryWrapper<WorkflowEdgePo>()
                .eq(WorkflowEdgePo::getWorkflowId, workflowId));
    }

    private WorkflowPo getWorkflowOrThrow(Long id) {
        WorkflowPo workflow = workflowMapper.selectById(id);
        if (workflow == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流不存在");
        }
        return workflow;
    }

    private WorkflowDetailResponse buildDetail(WorkflowPo workflow) {
        WorkflowDetailResponse response = new WorkflowDetailResponse();
        fillBaseResponse(workflow, response);
        response.setNodes(listNodes(workflow.getId()).stream().map(this::toNodeResponse).toList());
        response.setEdges(listEdges(workflow.getId()).stream().map(this::toEdgeResponse).toList());
        return response;
    }

    private WorkflowListResponse toListResponse(WorkflowPo workflow) {
        WorkflowListResponse response = new WorkflowListResponse();
        fillBaseResponse(workflow, response);
        return response;
    }

    private void fillBaseResponse(WorkflowPo workflow, WorkflowListResponse response) {
        response.setId(workflow.getId());
        response.setName(workflow.getName());
        response.setDescription(workflow.getDescription());
        response.setStatus(workflow.getStatus());
        response.setVersion(workflow.getVersion());
        response.setStartNodeId(workflow.getStartNodeId());
        response.setConfig(workflow.getConfigJson());
        response.setCreatedAt(workflow.getCreatedAt());
        response.setUpdatedAt(workflow.getUpdatedAt());
    }

    private List<WorkflowNodePo> listNodes(Long workflowId) {
        return workflowNodeMapper.selectList(new LambdaQueryWrapper<WorkflowNodePo>()
                .eq(WorkflowNodePo::getWorkflowId, workflowId)
                .orderByAsc(WorkflowNodePo::getId));
    }

    private List<WorkflowEdgePo> listEdges(Long workflowId) {
        return workflowEdgeMapper.selectList(new LambdaQueryWrapper<WorkflowEdgePo>()
                .eq(WorkflowEdgePo::getWorkflowId, workflowId)
                .orderByAsc(WorkflowEdgePo::getPriority)
                .orderByAsc(WorkflowEdgePo::getId));
    }

    private WorkflowNodeResponse toNodeResponse(WorkflowNodePo po) {
        WorkflowNodeResponse response = new WorkflowNodeResponse();
        response.setNodeId(po.getNodeId());
        response.setNodeType(WorkflowNodeType.from(po.getNodeType()));
        response.setName(po.getName());
        response.setConfig(po.getConfigJson());
        response.setPosition(po.getPositionJson());
        return response;
    }

    private WorkflowEdgeResponse toEdgeResponse(WorkflowEdgePo po) {
        WorkflowEdgeResponse response = new WorkflowEdgeResponse();
        response.setSourceNodeId(po.getSourceNodeId());
        response.setTargetNodeId(po.getTargetNodeId());
        response.setEdgeType(WorkflowEdgeType.from(po.getEdgeType()));
        response.setConditionExpression(po.getConditionExpression());
        response.setPriority(po.getPriority());
        return response;
    }

    private String defaultStatus(String status) {
        return StringUtils.hasText(status) ? status : DEFAULT_STATUS;
    }

    private String defaultString(String value) {
        return value != null ? value : "";
    }

    private <T> T defaultIfNull(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

    private List<WorkflowEdgeRequest> emptyIfNull(List<WorkflowEdgeRequest> edges) {
        return edges != null ? edges : Collections.emptyList();
    }

    private int countList(List<?> list) {
        return list != null ? list.size() : 0;
    }
}
