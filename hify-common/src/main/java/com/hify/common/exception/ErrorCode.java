package com.hify.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    PARAM_ERROR(422, "参数校验失败"),
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
