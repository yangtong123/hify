package com.hify.modules.workflow.domain.model;

import java.util.Map;

public record ToolNodeConfig(
        String toolCode,
        Map<String, String> inputMapping,
        String outputVariable
) implements NodeConfig {
}
