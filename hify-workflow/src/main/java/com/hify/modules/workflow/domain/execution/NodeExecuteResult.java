package com.hify.modules.workflow.domain.execution;

import java.util.Collections;
import java.util.Map;

public record NodeExecuteResult(
        boolean success,
        boolean stop,
        Map<String, Object> output,
        String errorMessage
) {

    public static NodeExecuteResult success(Map<String, Object> output) {
        return new NodeExecuteResult(true, false, output != null ? output : Collections.emptyMap(), null);
    }

    public static NodeExecuteResult stop(Map<String, Object> output) {
        return new NodeExecuteResult(true, true, output != null ? output : Collections.emptyMap(), null);
    }

    public static NodeExecuteResult failed(String errorMessage) {
        return new NodeExecuteResult(false, false, Collections.emptyMap(), errorMessage);
    }
}
