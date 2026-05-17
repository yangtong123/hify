package com.hify.modules.workflow.domain.model;

public sealed interface NodeConfig permits
        StartNodeConfig,
        LlmNodeConfig,
        KnowledgeNodeConfig,
        ConditionNodeConfig,
        ToolNodeConfig,
        EndNodeConfig {
}
