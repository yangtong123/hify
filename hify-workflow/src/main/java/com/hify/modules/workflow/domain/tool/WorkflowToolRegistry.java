package com.hify.modules.workflow.domain.tool;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class WorkflowToolRegistry {

    private final Map<String, WorkflowToolInvoker> invokerMap;

    public WorkflowToolRegistry(List<WorkflowToolInvoker> invokers) {
        this.invokerMap = invokers.stream()
                .collect(Collectors.toMap(WorkflowToolInvoker::toolCode, Function.identity()));
    }

    public WorkflowToolInvoker get(String toolCode) {
        WorkflowToolInvoker invoker = invokerMap.get(toolCode);
        if (invoker == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "未注册工作流工具: " + toolCode);
        }
        return invoker;
    }
}
