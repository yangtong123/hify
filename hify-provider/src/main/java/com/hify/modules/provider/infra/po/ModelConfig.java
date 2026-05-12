package com.hify.modules.provider.infra.po;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class ModelConfig {

    private Capabilities capabilities;
    private DefaultParams defaultParams;
    private Pricing pricing;

    @Data
    public static class Capabilities {
        private Boolean streaming;
        private Boolean vision;
        private Boolean thinking;
    }

    @Data
    public static class DefaultParams {
        private BigDecimal temperature;
        private BigDecimal topP;
        private BigDecimal frequencyPenalty;
        private BigDecimal presencePenalty;
        private Map<String, Object> thinkingConfig;
    }

    @Data
    public static class Pricing {
        private BigDecimal inputPer1m;
        private BigDecimal outputPer1m;
        private String currency;
    }
}
