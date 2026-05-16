package com.hify.modules.workflow.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class WorkflowQuery {

    private String name;

    private String status;

    @Min(value = 1, message = "page 不能小于 1")
    private Integer page = 1;

    @Min(value = 1, message = "size 不能小于 1")
    @Max(value = 100, message = "size 不能大于 100")
    private Integer size = 20;
}
