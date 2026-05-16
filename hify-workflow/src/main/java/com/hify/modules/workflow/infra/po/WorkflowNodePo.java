package com.hify.modules.workflow.infra.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.hify.common.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_workflow_node", autoResultMap = true)
public class WorkflowNodePo extends BaseEntity {

    @TableField("workflow_id")
    private Long workflowId;

    @TableField("node_id")
    private String nodeId;

    @TableField("node_type")
    private String nodeType;

    @TableField("name")
    private String name;

    @TableField(value = "config_json", typeHandler = JacksonTypeHandler.class)
    private JsonNode configJson;

    @TableField(value = "position_json", typeHandler = JacksonTypeHandler.class)
    private JsonNode positionJson;
}
