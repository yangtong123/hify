package com.hify.mcp.refund.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrderInfo {

    private final String orderId;

    private final String userId;

    private final BigDecimal amount;

    private final boolean signed;

    private final LocalDateTime signedAt;

    public OrderInfo(String orderId, String userId, BigDecimal amount, boolean signed, LocalDateTime signedAt) {
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.signed = signed;
        this.signedAt = signedAt;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getUserId() {
        return userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public boolean isSigned() {
        return signed;
    }

    public LocalDateTime getSignedAt() {
        return signedAt;
    }
}
