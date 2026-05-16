package com.hify.modules.workflow.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;

import java.util.Locale;

public enum WorkflowEdgeType {

    NORMAL,
    CONDITION,
    ERROR;

    @JsonCreator
    public static WorkflowEdgeType from(String value) {
        if (value == null || value.isBlank()) {
            return NORMAL;
        }
        try {
            return WorkflowEdgeType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的连接类型: " + value);
        }
    }

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
