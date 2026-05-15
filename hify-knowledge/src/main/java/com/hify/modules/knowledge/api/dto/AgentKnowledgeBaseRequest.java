package com.hify.modules.knowledge.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class AgentKnowledgeBaseRequest {

    private List<Long> knowledgeBaseIds;
}
