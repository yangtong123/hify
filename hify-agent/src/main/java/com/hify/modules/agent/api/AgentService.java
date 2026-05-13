package com.hify.modules.agent.api;

import com.hify.common.web.PageResult;
import com.hify.modules.agent.api.dto.AgentCreateRequest;
import com.hify.modules.agent.api.dto.AgentDetailResponse;
import com.hify.modules.agent.api.dto.AgentListResponse;
import com.hify.modules.agent.api.dto.AgentQuery;
import com.hify.modules.agent.api.dto.AgentToolsRequest;
import com.hify.modules.agent.api.dto.AgentUpdateRequest;

import java.util.List;

public interface AgentService {

    AgentDetailResponse create(AgentCreateRequest request);

    AgentDetailResponse update(Long id, AgentUpdateRequest request);

    void delete(Long id);

    AgentDetailResponse getById(Long id);

    PageResult<List<AgentListResponse>> list(AgentQuery query);

    void updateTools(Long agentId, AgentToolsRequest request);
}
