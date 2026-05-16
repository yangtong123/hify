package com.hify.modules.workflow.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.workflow.infra.po.WorkflowNodePo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface WorkflowNodeMapper extends BaseMapper<WorkflowNodePo> {

    @Insert("""
            <script>
            INSERT INTO t_workflow_node
            (workflow_id, node_id, node_type, name, config_json, position_json, created_at, updated_at, deleted)
            VALUES
            <foreach collection="nodes" item="item" separator=",">
            (#{item.workflowId}, #{item.nodeId}, #{item.nodeType}, #{item.name},
             #{item.configJson,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
             #{item.positionJson,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
             #{item.createdAt}, #{item.updatedAt}, #{item.deleted})
            </foreach>
            </script>
            """)
    int batchInsert(@Param("nodes") List<WorkflowNodePo> nodes);
}
