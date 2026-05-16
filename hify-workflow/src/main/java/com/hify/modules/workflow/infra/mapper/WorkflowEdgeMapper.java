package com.hify.modules.workflow.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.workflow.infra.po.WorkflowEdgePo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface WorkflowEdgeMapper extends BaseMapper<WorkflowEdgePo> {

    @Insert("""
            <script>
            INSERT INTO t_workflow_edge
            (workflow_id, source_node_id, target_node_id, edge_type, condition_expression, priority,
             created_at, updated_at, deleted)
            VALUES
            <foreach collection="edges" item="item" separator=",">
            (#{item.workflowId}, #{item.sourceNodeId}, #{item.targetNodeId}, #{item.edgeType},
             #{item.conditionExpression}, #{item.priority}, #{item.createdAt}, #{item.updatedAt}, #{item.deleted})
            </foreach>
            </script>
            """)
    int batchInsert(@Param("edges") List<WorkflowEdgePo> edges);
}
