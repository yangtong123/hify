package com.hify.modules.chat.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatSendRequest {

    private Long agentId;

    private Long sessionId;

    @NotBlank(message = "用户标识不能为空")
    private String userId;

    @NotBlank(message = "消息内容不能为空")
    private String content;
}
