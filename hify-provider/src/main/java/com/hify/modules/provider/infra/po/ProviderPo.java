package com.hify.modules.provider.infra.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_provider", autoResultMap = true)
public class ProviderPo extends BaseEntity {

    @TableField("name")
    private String name;

    @TableField("type")
    private String type;

    @TableField("base_url")
    private String baseUrl;

    @TableField(value = "auth_config", typeHandler = JacksonTypeHandler.class)
    private AuthConfig authConfig;

    @TableField("enabled")
    private Integer enabled;
}
