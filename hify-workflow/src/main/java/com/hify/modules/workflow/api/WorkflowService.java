package com.hify.modules.workflow.api;

import com.hify.common.web.PageResult;
import com.hify.modules.workflow.api.dto.WorkflowCreateRequest;
import com.hify.modules.workflow.api.dto.WorkflowDetailResponse;
import com.hify.modules.workflow.api.dto.WorkflowListResponse;
import com.hify.modules.workflow.api.dto.WorkflowQuery;
import com.hify.modules.workflow.api.dto.WorkflowUpdateRequest;

import java.util.List;

public interface WorkflowService {

    WorkflowDetailResponse create(WorkflowCreateRequest request);

    PageResult<List<WorkflowListResponse>> list(WorkflowQuery query);

    WorkflowDetailResponse getById(Long id);

    WorkflowDetailResponse update(Long id, WorkflowUpdateRequest request);

    void delete(Long id);
}
