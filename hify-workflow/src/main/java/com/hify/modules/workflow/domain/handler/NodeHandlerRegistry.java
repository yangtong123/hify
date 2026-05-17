package com.hify.modules.workflow.domain.handler;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.workflow.api.dto.WorkflowNodeType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NodeHandlerRegistry {

    private final Map<WorkflowNodeType, WorkflowNodeHandler> handlerMap;

    public NodeHandlerRegistry(List<WorkflowNodeHandler> handlers) {
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(WorkflowNodeHandler::supportType, Function.identity()));
    }

    public WorkflowNodeHandler get(WorkflowNodeType nodeType) {
        WorkflowNodeHandler handler = handlerMap.get(nodeType);
        if (handler == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "未注册节点处理器: " + nodeType.value());
        }
        return handler;
    }
}
