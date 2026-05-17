package com.hify.modules.workflow.api.dto.run;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class WorkflowNodeRunResponse {

    private Long id;

    private Long workflowRunId;

    private String nodeId;

    private String nodeType;

    private String status;

    private Map<String, Object> input;

    private Map<String, Object> output;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Long elapsedMs;
}
