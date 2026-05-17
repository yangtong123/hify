package com.hify.modules.workflow.api.dto.run;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkflowRunDetailResponse extends WorkflowRunResponse {

    private List<WorkflowNodeRunResponse> nodes;
}
