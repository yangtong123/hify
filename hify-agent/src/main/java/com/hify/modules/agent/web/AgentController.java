package com.hify.modules.agent.web;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.modules.agent.api.AgentService;
import com.hify.modules.agent.api.dto.AgentCreateRequest;
import com.hify.modules.agent.api.dto.AgentDetailResponse;
import com.hify.modules.agent.api.dto.AgentListResponse;
import com.hify.modules.agent.api.dto.AgentQuery;
import com.hify.modules.agent.api.dto.AgentToolsRequest;
import com.hify.modules.agent.api.dto.AgentUpdateRequest;
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
@RequestMapping("/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping
    public Result<AgentDetailResponse> create(@Valid @RequestBody AgentCreateRequest request) {
        return Result.ok(agentService.create(request));
    }

    @PutMapping("/{id}")
    public Result<AgentDetailResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody AgentUpdateRequest request) {
        return Result.ok(agentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        agentService.delete(id);
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result<AgentDetailResponse> getById(@PathVariable Long id) {
        return Result.ok(agentService.getById(id));
    }

    @GetMapping
    public PageResult<List<AgentListResponse>> list(@Valid AgentQuery query) {
        return agentService.list(query);
    }

    @PutMapping("/{id}/tools")
    public Result<Void> updateTools(@PathVariable Long id,
                                     @Valid @RequestBody AgentToolsRequest request) {
        agentService.updateTools(id, request);
        return Result.ok();
    }
}
