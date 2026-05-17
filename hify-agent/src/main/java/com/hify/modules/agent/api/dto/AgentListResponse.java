package com.hify.modules.agent.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AgentListResponse {

    private Long id;
    private String name;
    private String description;
    private String systemPrompt;
    private Long modelConfigId;
    private Long workflowId;
    private String modelName;
    private String providerName;
    private BigDecimal temperature;
    private Integer maxTokens;
    private BigDecimal topP;
    private Integer maxContextTurns;
    private AgentConfigDto configJson;
    private Integer enabled;
    private Integer mcpServerCount;
    private Integer knowledgeBaseCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
