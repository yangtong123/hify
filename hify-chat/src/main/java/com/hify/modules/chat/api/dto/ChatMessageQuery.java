package com.hify.modules.chat.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessageQuery {

    private LocalDateTime beforeCreatedAt;

    private Long beforeId;

    @Min(value = 1, message = "size 最小为 1")
    @Max(value = 100, message = "size 最大为 100")
    private int size = 20;
}
