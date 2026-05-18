package com.hify.mcp.refund.infra;

import com.hify.mcp.refund.domain.RefundApplication;
import com.hify.mcp.refund.domain.RefundStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<RefundApplication, Long> {

    Optional<RefundApplication> findTopByOrderIdOrderByCreatedAtDesc(String orderId);

    Optional<RefundApplication> findTopByOrderIdAndStatusInOrderByCreatedAtDesc(
            String orderId, Collection<RefundStatus> statuses);
}
