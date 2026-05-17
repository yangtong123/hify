package com.hify.modules.workflow.domain.execution;

import java.util.LinkedHashMap;
import java.util.Map;

public class WorkflowContext {

    private final Long runId;
    private final Long workflowId;
    private final String userId;
    private final Map<String, Object> inputs;
    private final Map<String, Object> variables = new LinkedHashMap<>();
    private final Map<String, Object> outputs = new LinkedHashMap<>();

    public WorkflowContext(Long runId, Long workflowId, String userId, Map<String, Object> inputs) {
        this.runId = runId;
        this.workflowId = workflowId;
        this.userId = userId;
        this.inputs = inputs != null ? new LinkedHashMap<>(inputs) : new LinkedHashMap<>();
    }

    public Long getRunId() {
        return runId;
    }

    public Long getWorkflowId() {
        return workflowId;
    }

    public String getUserId() {
        return userId;
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public Map<String, Object> getOutputs() {
        return outputs;
    }

    public void putNodeOutput(String nodeId, String outputVariable, Object value) {
        String key = nodeId + "." + outputVariable;
        variables.put(key, value);
        outputs.put(key, value);
    }

    public Object resolve(String expression) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        String key = normalize(expression);
        if (key.startsWith("inputs.")) {
            return readPath(inputs, key.substring("inputs.".length()));
        }
        if (key.startsWith("variables.")) {
            return readPath(variables, key.substring("variables.".length()));
        }
        if (key.startsWith("outputs.")) {
            return readPath(outputs, key.substring("outputs.".length()));
        }
        Object variable = readPath(variables, key);
        if (variable != null) {
            return variable;
        }
        return readPath(inputs, key);
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("inputs", new LinkedHashMap<>(inputs));
        snapshot.put("variables", new LinkedHashMap<>(variables));
        snapshot.put("outputs", new LinkedHashMap<>(outputs));
        return snapshot;
    }

    private String normalize(String expression) {
        String value = expression.trim();
        if (value.startsWith("$.context.")) {
            return value.substring("$.context.".length());
        }
        if (value.startsWith("$.inputs.")) {
            return "inputs." + value.substring("$.inputs.".length());
        }
        if (value.startsWith("$.")) {
            return value.substring(2);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Object readPath(Map<String, Object> source, String path) {
        if (source.containsKey(path)) {
            return source.get(path);
        }
        String[] parts = path.split("\\.");
        Object current = source;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }
}
