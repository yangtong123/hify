package com.hify.modules.workflow.domain.model;

import java.math.BigDecimal;

public record LlmNodeConfig(
        Long modelConfigId,
        String systemPrompt,
        String userPromptTemplate,
        BigDecimal temperature,
        String outputVariable
) implements NodeConfig {
}
