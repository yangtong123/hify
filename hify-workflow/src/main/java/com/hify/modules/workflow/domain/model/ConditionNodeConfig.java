package com.hify.modules.workflow.domain.model;

public record ConditionNodeConfig(
        String expression
) implements NodeConfig {
}
