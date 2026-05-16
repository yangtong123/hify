package com.hify.modules.workflow.api.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkflowDetailResponse extends WorkflowListResponse {

    private List<WorkflowNodeResponse> nodes;

    private List<WorkflowEdgeResponse> edges;
}
