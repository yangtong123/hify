package com.hify.modules.provider.infra.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_model_config", autoResultMap = true)
public class ModelConfigPo extends BaseEntity {

    @TableField("provider_id")
    private Long providerId;

    @TableField("name")
    private String name;

    @TableField("model_id")
    private String modelId;

    @TableField("context_size")
    private Integer contextSize;

    @TableField(value = "extra_params", typeHandler = JacksonTypeHandler.class)
    private ModelConfig extraParams;

    @TableField("enabled")
    private Integer enabled;
}
