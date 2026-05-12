package com.hify.modules.provider.infra.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_provider_health")
public class ProviderHealthCheckPo {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("provider_id")
    private Long providerId;

    @TableField("status")
    private String status;

    @TableField("last_check_at")
    private LocalDateTime lastCheckAt;

    @TableField("last_success_at")
    private LocalDateTime lastSuccessAt;

    @TableField("fail_count")
    private Integer failCount;

    @TableField("latency_ms")
    private Integer latencyMs;

    @TableField("error_message")
    private String errorMessage;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
