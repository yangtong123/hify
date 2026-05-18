package com.hify.mcp.refund.domain;

import java.util.Optional;

public interface OrderLookupService {

    Optional<OrderInfo> getOrder(String orderId);
}
