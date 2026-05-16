package com.hify.modules.workflow.web;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.modules.workflow.api.WorkflowService;
import com.hify.modules.workflow.api.dto.WorkflowCreateRequest;
import com.hify.modules.workflow.api.dto.WorkflowDetailResponse;
import com.hify.modules.workflow.api.dto.WorkflowListResponse;
import com.hify.modules.workflow.api.dto.WorkflowQuery;
import com.hify.modules.workflow.api.dto.WorkflowUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @PostMapping
    public Result<WorkflowDetailResponse> create(@Valid @RequestBody WorkflowCreateRequest request) {
        return Result.ok(workflowService.create(request));
    }

    @GetMapping
    public PageResult<List<WorkflowListResponse>> list(@Valid WorkflowQuery query) {
        return workflowService.list(query);
    }

    @GetMapping("/{id}")
    public Result<WorkflowDetailResponse> getById(@PathVariable Long id) {
        return Result.ok(workflowService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<WorkflowDetailResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody WorkflowUpdateRequest request) {
        return Result.ok(workflowService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workflowService.delete(id);
        return Result.ok();
    }
}
