package com.hify.modules.provider.api.dto;

import lombok.Data;

@Data
public class ConnectionTestResult {

    private boolean success;
    private long latencyMs;
    private Integer modelCount;
    private String errorMessage;

    public static ConnectionTestResult success(long latencyMs, int modelCount) {
        ConnectionTestResult r = new ConnectionTestResult();
        r.success = true;
        r.latencyMs = latencyMs;
        r.modelCount = modelCount;
        return r;
    }

    public static ConnectionTestResult fail(String errorMessage) {
        ConnectionTestResult r = new ConnectionTestResult();
        r.success = false;
        r.errorMessage = errorMessage;
        return r;
    }
}
