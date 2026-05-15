package com.hify.modules.knowledge.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class KnowledgeBaseQuery {

    private String name;

    private String status;

    @Min(value = 1, message = "page 不能小于 1")
    private int page = 1;

    @Min(value = 1, message = "size 不能小于 1")
    @Max(value = 100, message = "size 不能大于 100")
    private int size = 20;
}
