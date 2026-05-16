package com.hify.modules.workflow.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.modules.workflow.api.dto.WorkflowNodeType;
import com.hify.modules.workflow.domain.model.ConditionNodeConfig;
import com.hify.modules.workflow.domain.model.EndNodeConfig;
import com.hify.modules.workflow.domain.model.LlmNodeConfig;
import com.hify.modules.workflow.domain.model.NodeConfig;
import com.hify.modules.workflow.domain.model.StartNodeConfig;
import com.hify.modules.workflow.domain.model.ToolNodeConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NodeConfigParser {

    private final ObjectMapper objectMapper;

    public NodeConfig parse(WorkflowNodeType nodeType, JsonNode config) {
        JsonNode configNode = config != null && !config.isNull() ? config : objectMapper.createObjectNode();
        try {
            NodeConfig nodeConfig = switch (nodeType) {
                case START -> objectMapper.treeToValue(configNode, StartNodeConfig.class);
                case LLM -> objectMapper.treeToValue(configNode, LlmNodeConfig.class);
                case CONDITION -> objectMapper.treeToValue(configNode, ConditionNodeConfig.class);
                case TOOL -> objectMapper.treeToValue(configNode, ToolNodeConfig.class);
                case END -> objectMapper.treeToValue(configNode, EndNodeConfig.class);
            };
            validate(nodeType, nodeConfig);
            return nodeConfig;
        } catch (JsonProcessingException exception) {
            throw new BizException(ErrorCode.PARAM_ERROR, "节点配置格式错误: " + nodeType.value());
        }
    }

    private void validate(WorkflowNodeType nodeType, NodeConfig config) {
        if (config instanceof StartNodeConfig
                || config instanceof LlmNodeConfig
                || config instanceof ConditionNodeConfig
                || config instanceof ToolNodeConfig
                || config instanceof EndNodeConfig) {
            return;
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "节点配置类型不匹配: " + nodeType.value());
    }
}
