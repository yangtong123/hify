package com.hify.mcp.refund.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.mcp.refund.domain.RefundService;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class McpConfig {

    @Bean
    public WebMvcSseServerTransportProvider webMvcSseServerTransportProvider() {
        return WebMvcSseServerTransportProvider.builder()
                .messageEndpoint("/messages")
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> mcpRouterFunction(WebMvcSseServerTransportProvider transportProvider) {
        return transportProvider.getRouterFunction();
    }

    @Bean
    public McpSyncServer mcpSyncServer(WebMvcSseServerTransportProvider transportProvider,
                                       RefundService refundService,
                                       ObjectMapper objectMapper) {
        return McpServer.sync(transportProvider)
                .serverInfo("refund-mcp-server", "1.0.0")
                .tools(List.of(
                        checkRefundEligibilityTool(refundService, objectMapper),
                        submitRefundTool(refundService, objectMapper),
                        getRefundStatusTool(refundService, objectMapper),
                        cancelRefundTool(refundService, objectMapper)))
                .build();
    }

    private McpServerFeatures.SyncToolSpecification checkRefundEligibilityTool(RefundService refundService,
                                                                               ObjectMapper objectMapper) {
        return tool(
                "check_refund_eligibility",
                "查询订单退款资格。用户说“我要退款”“能不能退”“不想要了”等表达退款意愿时，先调此工具确认是否符合条件，再决定是否提交申请。不要跳过此步直接提交。",
                schema(
                        Map.of("orderId", stringProperty("订单号，用于查询该订单是否在退款期内。")),
                        List.of("orderId")),
                (exchange, request) -> {
                    Map<String, Object> result = refundService.checkEligibility(stringArg(request, "orderId"));
                    return result(objectMapper, result, isBusinessError(result));
                });
    }

    private McpServerFeatures.SyncToolSpecification submitRefundTool(RefundService refundService,
                                                                     ObjectMapper objectMapper) {
        return tool(
                "submit_refund",
                "提交退款申请。仅在用户明确确认退款意愿、且 check_refund_eligibility 返回 eligible=true 后调用。提交前应确认订单号、用户、退款金额和退款原因。",
                schema(
                        Map.of(
                                "orderId", stringProperty("订单号。"),
                                "userId", stringProperty("用户编号。"),
                                "amount", numberProperty("退款金额。"),
                                "reason", stringProperty("退款原因，使用用户自然语言说明。")),
                        List.of("orderId", "userId", "amount", "reason")),
                (exchange, request) -> {
                    Map<String, Object> result = refundService.submitRefund(
                            stringArg(request, "orderId"),
                            stringArg(request, "userId"),
                            decimalArg(request, "amount"),
                            stringArg(request, "reason"));
                    return result(objectMapper, result, isBusinessError(result));
                });
    }

    private McpServerFeatures.SyncToolSpecification getRefundStatusTool(RefundService refundService,
                                                                        ObjectMapper objectMapper) {
        return tool(
                "get_refund_status",
                "查询退款进度。用户询问“退款到哪了”“多久到账”“退款成功了吗”“为什么还没退”等退款状态问题时调用。",
                schema(
                        Map.of("orderId", stringProperty("订单号，用于查询该订单最新的退款申请。")),
                        List.of("orderId")),
                (exchange, request) -> {
                    Map<String, Object> result = refundService.getStatus(stringArg(request, "orderId"));
                    return result(objectMapper, result, isBusinessError(result));
                });
    }

    private McpServerFeatures.SyncToolSpecification cancelRefundTool(RefundService refundService,
                                                                    ObjectMapper objectMapper) {
        return tool(
                "cancel_refund",
                "撤销退款申请。用户明确表示“不退了”“取消退款”“撤回退款申请”时调用。只有待审核 PENDING 状态可以撤销，处理中或已完成的退款不能撤销。",
                schema(
                        Map.of("refundId", integerProperty("退款申请编号。")),
                        List.of("refundId")),
                (exchange, request) -> {
                    Map<String, Object> result = refundService.cancelRefund(longArg(request, "refundId"));
                    return result(objectMapper, result, isBusinessError(result));
                });
    }

    private McpServerFeatures.SyncToolSpecification tool(
            String name,
            String description,
            McpSchema.JsonSchema inputSchema,
            java.util.function.BiFunction<
                    io.modelcontextprotocol.server.McpSyncServerExchange,
                    McpSchema.CallToolRequest,
                    McpSchema.CallToolResult> handler) {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder()
                        .name(name)
                        .description(description)
                        .inputSchema(inputSchema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        return handler.apply(exchange, request);
                    } catch (Exception e) {
                        return textResult("{\"error\":\"工具调用失败，请稍后再试或转人工处理。\"}", true);
                    }
                })
                .build();
    }

    private McpSchema.JsonSchema schema(Map<String, Object> properties, List<String> required) {
        return new McpSchema.JsonSchema("object", properties, required, false, null, null);
    }

    private Map<String, Object> stringProperty(String description) {
        return Map.of("type", "string", "description", description);
    }

    private Map<String, Object> numberProperty(String description) {
        return Map.of("type", "number", "description", description);
    }

    private Map<String, Object> integerProperty(String description) {
        return Map.of("type", "integer", "description", description);
    }

    private McpSchema.CallToolResult result(ObjectMapper objectMapper, Map<String, Object> result, boolean isError) {
        try {
            return textResult(objectMapper.writeValueAsString(result), isError);
        } catch (Exception e) {
            return textResult("{\"error\":\"工具结果序列化失败，请稍后再试或转人工处理。\"}", true);
        }
    }

    private McpSchema.CallToolResult textResult(String json, boolean isError) {
        return McpSchema.CallToolResult.builder()
                .addTextContent(json)
                .isError(isError)
                .build();
    }

    private boolean isBusinessError(Map<String, Object> result) {
        return result.containsKey("error") || Boolean.FALSE.equals(result.get("success"));
    }

    private String stringArg(McpSchema.CallToolRequest request, String name) {
        Object value = request.arguments().get(name);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.toString();
    }

    private BigDecimal decimalArg(McpSchema.CallToolRequest request, String name) {
        Object value = request.arguments().get(name);
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    private Long longArg(McpSchema.CallToolRequest request, String name) {
        Object value = request.arguments().get(name);
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }
}
