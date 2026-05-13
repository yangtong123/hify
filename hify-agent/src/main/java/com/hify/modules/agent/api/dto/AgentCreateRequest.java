package com.hify.modules.agent.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AgentCreateRequest {

    @NotBlank(message = "Agent 名称不能为空")
    private String name;

    private String description;

    private String systemPrompt;

    @NotNull(message = "模型配置不能为空")
    private Long modelConfigId;

    @DecimalMin(value = "0.00", message = "temperature 不能小于 0")
    @DecimalMax(value = "2.00", message = "temperature 不能大于 2")
    private BigDecimal temperature;

    @Min(value = 1, message = "maxTokens 不能小于 1")
    @Max(value = 200000, message = "maxTokens 不能大于 200000")
    private Integer maxTokens;

    @DecimalMin(value = "0.00", message = "topP 不能小于 0")
    @DecimalMax(value = "1.00", message = "topP 不能大于 1")
    private BigDecimal topP;

    @Min(value = 1, message = "maxContextTurns 不能小于 1")
    @Max(value = 100, message = "maxContextTurns 不能大于 100")
    private Integer maxContextTurns;

    private AgentConfigDto configJson;

    private Integer enabled;

    private List<Long> mcpServerIds;
}
