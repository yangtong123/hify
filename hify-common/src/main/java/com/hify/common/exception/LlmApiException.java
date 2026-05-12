package com.hify.common.exception;

import lombok.Getter;

@Getter
public class LlmApiException extends RuntimeException {

    public enum ErrorType {
        TIMEOUT,
        AUTH_FAILED,
        RATE_LIMITED,
        API_ERROR
    }

    private final ErrorType errorType;
    private final int httpStatus;

    public LlmApiException(ErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
        this.httpStatus = 0;
    }

    public LlmApiException(ErrorType errorType, int httpStatus, String message) {
        super(message);
        this.errorType = errorType;
        this.httpStatus = httpStatus;
    }

    public LlmApiException(ErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.httpStatus = 0;
    }
}
