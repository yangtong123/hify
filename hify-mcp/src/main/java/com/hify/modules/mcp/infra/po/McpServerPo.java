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
@TableName(value = "t_mcp_server", autoResultMap = true)
public class McpServerPo extends BaseEntity {

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("server_type")
    private String serverType;

    @TableField("command")
    private String command;

    @TableField("args")
    private String args;

    @TableField("url")
    private String url;

    @TableField("api_key")
    private String apiKey;

    @TableField("is_enabled")
    private Integer isEnabled;

    @TableField(value = "config_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> configJson;
}
