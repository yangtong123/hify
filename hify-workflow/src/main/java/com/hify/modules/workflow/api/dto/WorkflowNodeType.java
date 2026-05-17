package com.hify.modules.workflow.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;

import java.util.Locale;

public enum WorkflowNodeType {

    START,
    LLM,
    KNOWLEDGE,
    CONDITION,
    TOOL,
    END;

    @JsonCreator
    public static WorkflowNodeType from(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "节点类型不能为空");
        }
        try {
            return WorkflowNodeType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的节点类型: " + value);
        }
    }

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
