package com.hify.modules.mcp.infra.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_agent_mcp")
public class AgentMcpBindingPo extends BaseEntity {

    @TableField("agent_id")
    private Long agentId;

    @TableField("mcp_server_id")
    private Long mcpServerId;
}
