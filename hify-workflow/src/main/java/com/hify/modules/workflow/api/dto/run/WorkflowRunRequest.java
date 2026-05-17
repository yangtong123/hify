package com.hify.modules.workflow.api.dto.run;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class WorkflowRunRequest {

    @NotBlank(message = "用户标识不能为空")
    private String userId;

    private Map<String, Object> inputs;
}
