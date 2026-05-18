package com.hify.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    PARAM_ERROR(422, "参数校验失败"),
    MCP_TOOL_CALL_FAILED(502, "MCP 工具调用失败"),
    MCP_SERVER_NOT_FOUND(404, "MCP Server 不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),
    THIRD_PARTY_ERROR(502, "第三方服务异常"),
    ;

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
