package com.hify.modules.workflow.infra.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.hify.common.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_workflow", autoResultMap = true)
public class WorkflowPo extends BaseEntity {

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("status")
    private String status;

    @TableField("version")
    private Integer version;

    @TableField("start_node_id")
    private String startNodeId;

    @TableField(value = "config_json", typeHandler = JacksonTypeHandler.class, updateStrategy = FieldStrategy.ALWAYS)
    private JsonNode configJson;
}
