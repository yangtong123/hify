package com.hify.modules.agent.infra.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_agent", autoResultMap = true)
public class AgentPo extends BaseEntity {

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("system_prompt")
    private String systemPrompt;

    @TableField("model_config_id")
    private Long modelConfigId;

    @TableField("temperature")
    private BigDecimal temperature;

    @TableField("max_tokens")
    private Integer maxTokens;

    @TableField("top_p")
    private BigDecimal topP;

    @TableField("max_context_turns")
    private Integer maxContextTurns;

    @TableField(value = "config_json", typeHandler = JacksonTypeHandler.class)
    private AgentConfig configJson;

    @TableField("enabled")
    private Integer enabled;
}
