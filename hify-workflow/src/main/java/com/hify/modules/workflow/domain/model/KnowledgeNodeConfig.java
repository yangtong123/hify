package com.hify.modules.workflow.domain.model;

import java.math.BigDecimal;
import java.util.List;

public record KnowledgeNodeConfig(
        List<Long> knowledgeBaseIds,
        String queryTemplate,
        Integer topK,
        BigDecimal similarityThreshold,
        String outputVariable
) implements NodeConfig {
}
