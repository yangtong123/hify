package com.hify.modules.workflow.api.dto.run;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class WorkflowRunResponse {

    private Long id;

    private Long workflowId;

    private String status;

    private Map<String, Object> outputs;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Long elapsedMs;
}
