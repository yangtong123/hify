package com.hify.modules.mcp.infra.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_mcp_tool", autoResultMap = true)
public class McpToolPo extends BaseEntity {

    @TableField("mcp_server_id")
    private Long mcpServerId;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField(value = "input_schema", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> inputSchema;
}
