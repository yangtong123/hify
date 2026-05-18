package com.hify.mcp.refund.infra;

import com.hify.mcp.refund.domain.OrderInfo;
import com.hify.mcp.refund.domain.OrderLookupService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class MockOrderLookupService implements OrderLookupService {

    @Override
    public Optional<OrderInfo> getOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return Optional.empty();
        }
        if (orderId.startsWith("UNSIGNED")) {
            return Optional.of(new OrderInfo(orderId, "mock-user", new BigDecimal("99.00"), false, null));
        }
        if (orderId.startsWith("EXPIRED")) {
            return Optional.of(new OrderInfo(orderId, "mock-user", new BigDecimal("99.00"), true,
                    LocalDateTime.now().minusDays(8)));
        }
        return Optional.of(new OrderInfo(orderId, "mock-user", new BigDecimal("99.00"), true,
                LocalDateTime.now().minusDays(1)));
    }
}
