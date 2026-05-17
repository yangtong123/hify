package com.hify.modules.workflow.api;

import com.hify.modules.workflow.api.dto.run.WorkflowRunDetailResponse;
import com.hify.modules.workflow.api.dto.run.WorkflowRunRequest;
import com.hify.modules.workflow.api.dto.run.WorkflowRunResponse;

public interface WorkflowRunService {

    String execute(Long workflowId, String userContent);

    WorkflowRunResponse run(Long workflowId, WorkflowRunRequest request);

    WorkflowRunDetailResponse getRun(Long runId);
}
