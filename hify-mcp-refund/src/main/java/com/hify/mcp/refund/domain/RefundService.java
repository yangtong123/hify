package com.hify.mcp.refund.domain;

import com.hify.mcp.refund.infra.RefundRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundService {

    private static final int REFUND_WINDOW_DAYS = 7;

    private static final int ESTIMATED_DAYS = 3;

    private static final Collection<RefundStatus> ACTIVE_STATUSES = List.of(
            RefundStatus.PENDING,
            RefundStatus.APPROVED,
            RefundStatus.PROCESSING);

    private final RefundRepository refundRepository;

    private final OrderLookupService orderLookupService;

    public RefundService(RefundRepository refundRepository, OrderLookupService orderLookupService) {
        this.refundRepository = refundRepository;
        this.orderLookupService = orderLookupService;
    }

    public Map<String, Object> checkEligibility(String orderId) {
        try {
            return orderLookupService.getOrder(orderId)
                    .map(this::toEligibility)
                    .orElseGet(() -> errorEligibility("未找到该订单，请核对订单号后再试。"));
        } catch (Exception e) {
            return errorEligibility("暂时无法查询订单退款资格，请稍后再试或转人工处理。");
        }
    }

    @Transactional
    public Map<String, Object> submitRefund(String orderId, String userId, BigDecimal amount, String reason) {
        try {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return error("退款金额需要大于0，请重新确认退款金额。");
            }
            if (reason == null || reason.isBlank()) {
                return error("请提供退款原因，方便客服为您处理。");
            }

            return refundRepository.findTopByOrderIdAndStatusInOrderByCreatedAtDesc(orderId, ACTIVE_STATUSES)
                    .map(existing -> error("该订单已有进行中的退款申请，编号：" + existing.getId()))
                    .orElseGet(() -> createRefund(orderId, userId, amount, reason));
        } catch (Exception e) {
            return error("退款申请暂时提交失败，请稍后再试或转人工处理。");
        }
    }

    public Map<String, Object> getStatus(String orderId) {
        try {
            return refundRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId)
                    .map(this::toStatus)
                    .orElseGet(() -> error("未查询到该订单的退款申请记录。"));
        } catch (Exception e) {
            return error("暂时无法查询退款进度，请稍后再试或转人工处理。");
        }
    }

    @Transactional
    public Map<String, Object> cancelRefund(Long refundId) {
        try {
            return refundRepository.findById(refundId)
                    .map(this::cancelExistingRefund)
                    .orElseGet(() -> error("未找到该退款申请，请核对退款编号后再试。"));
        } catch (Exception e) {
            return error("退款申请暂时无法撤销，请稍后再试或转人工处理。");
        }
    }

    private Map<String, Object> toEligibility(OrderInfo order) {
        LocalDateTime deadlineAt = order.getSignedAt() == null ? null : order.getSignedAt().plusDays(REFUND_WINDOW_DAYS);
        LocalDate deadline = deadlineAt == null ? null : deadlineAt.toLocalDate();
        if (!order.isSigned()) {
            return eligibility(false, "订单还未签收，暂不支持按签收后退款规则申请。", deadline, order.getAmount());
        }
        if (deadlineAt == null || LocalDateTime.now().isAfter(deadlineAt)) {
            return eligibility(false, "订单已超过7天退款期，暂不支持自助退款。", deadline, order.getAmount());
        }
        return eligibility(true, "订单已签收且仍在7天退款期内，可以申请退款。", deadline, order.getAmount());
    }

    private Map<String, Object> createRefund(String orderId, String userId, BigDecimal amount, String reason) {
        RefundApplication application = new RefundApplication();
        application.setOrderId(orderId);
        application.setUserId(userId);
        application.setAmount(amount);
        application.setReason(reason);
        application.setStatus(RefundStatus.PENDING);
        RefundApplication saved = refundRepository.save(application);

        Map<String, Object> result = ok();
        result.put("refundId", saved.getId());
        result.put("status", saved.getStatus().name());
        result.put("statusLabel", statusLabel(saved.getStatus()));
        result.put("estimatedDays", ESTIMATED_DAYS);
        result.put("message", "退款申请已提交，预计3天内处理完成。");
        return result;
    }

    private Map<String, Object> toStatus(RefundApplication application) {
        Map<String, Object> result = ok();
        result.put("refundId", application.getId());
        result.put("orderId", application.getOrderId());
        result.put("amount", application.getAmount());
        result.put("status", application.getStatus().name());
        result.put("statusLabel", statusLabel(application.getStatus()));
        result.put("submittedAt", application.getCreatedAt());
        result.put("rejectReason", application.getRejectReason());
        result.put("message", statusMessage(application));
        return result;
    }

    private Map<String, Object> cancelExistingRefund(RefundApplication application) {
        if (application.getStatus() != RefundStatus.PENDING) {
            return error("退款已在处理中，无法撤销");
        }
        application.setStatus(RefundStatus.CANCELED);
        application.setRejectReason("用户已撤销退款申请");
        RefundApplication saved = refundRepository.save(application);

        Map<String, Object> result = ok();
        result.put("refundId", saved.getId());
        result.put("orderId", saved.getOrderId());
        result.put("status", saved.getStatus().name());
        result.put("statusLabel", statusLabel(saved.getStatus()));
        result.put("message", "退款申请已撤销。");
        return result;
    }

    private Map<String, Object> eligibility(boolean eligible, String reason, LocalDate deadline, BigDecimal amount) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eligible", eligible);
        result.put("reason", reason);
        result.put("deadline", deadline);
        result.put("amount", amount);
        return result;
    }

    private Map<String, Object> errorEligibility(String reason) {
        return eligibility(false, reason, null, null);
    }

    private Map<String, Object> ok() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return result;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("error", message);
        result.put("message", message);
        return result;
    }

    private String statusMessage(RefundApplication application) {
        if (application.getStatus() == RefundStatus.REJECTED && application.getRejectReason() != null) {
            return "退款申请已被拒绝，原因：" + application.getRejectReason();
        }
        return "退款当前状态：" + statusLabel(application.getStatus()) + "。";
    }

    private String statusLabel(RefundStatus status) {
        return switch (status) {
            case PENDING -> "待审核";
            case APPROVED -> "已审核通过";
            case PROCESSING -> "退款处理中";
            case COMPLETED -> "退款完成";
            case REJECTED -> "已拒绝";
            case CANCELED -> "已撤销";
        };
    }
}
