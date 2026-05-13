package com.hify.modules.chat.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatSessionQuery {

    @NotBlank(message = "用户标识不能为空")
    private String userId;

    private Long agentId;

    private String status;

    @Min(value = 1, message = "page 最小为 1")
    private int page = 1;

    @Min(value = 1, message = "size 最小为 1")
    @Max(value = 100, message = "size 最大为 100")
    private int size = 20;
}
