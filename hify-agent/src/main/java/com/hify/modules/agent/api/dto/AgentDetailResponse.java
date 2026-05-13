package com.hify.modules.agent.api.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class AgentDetailResponse extends AgentListResponse {

    private ModelInfo model;
    private List<McpServerInfo> mcpServers;

    @Data
    public static class ModelInfo {
        private Long id;
        private String name;
        private String providerName;
        private Integer contextSize;
    }

    @Data
    public static class McpServerInfo {
        private Long id;
        private String name;
        private String serverType;
        private Boolean isEnabled;
    }
}
