package com.hify.modules.workflow.infra.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_workflow_edge")
public class WorkflowEdgePo extends BaseEntity {

    @TableField("workflow_id")
    private Long workflowId;

    @TableField("source_node_id")
    private String sourceNodeId;

    @TableField("target_node_id")
    private String targetNodeId;

    @TableField("edge_type")
    private String edgeType;

    @TableField("condition_expression")
    private String conditionExpression;

    @TableField("priority")
    private Integer priority;
}
