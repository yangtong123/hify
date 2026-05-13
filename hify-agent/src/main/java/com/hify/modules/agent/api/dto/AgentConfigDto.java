package com.hify.modules.agent.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class AgentConfigDto {

    private String openingMessage;

    private List<String> suggestedQuestions;

    private Integer maxIterations;
}
