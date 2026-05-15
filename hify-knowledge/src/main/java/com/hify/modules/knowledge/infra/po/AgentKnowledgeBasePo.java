package com.hify.modules.knowledge.infra.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_agent_knowledge_base")
public class AgentKnowledgeBasePo extends BaseEntity {

    @TableField("agent_id")
    private Long agentId;

    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    @TableField("priority")
    private Integer priority;

    @TableField("enabled")
    private Integer enabled;
}
