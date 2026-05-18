package com.hify.common.metrics;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class HifyMetrics {

    private static final String UNKNOWN = "unknown";

    private final MeterRegistry meterRegistry;
    private final Set<String> circuitBreakerMeters = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void initBaseMeters() {
        registerChatMeters(UNKNOWN);
        registerLlmMeters(UNKNOWN, UNKNOWN, "success");
        registerLlmMeters(UNKNOWN, UNKNOWN, "failure");
        registerMcpToolMeters(UNKNOWN, UNKNOWN, "success");
        registerMcpToolMeters(UNKNOWN, UNKNOWN, "failure");
    }

    public void recordChatRequest(Long agentId, long startNanos) {
        String agentIdTag = tagValue(agentId);
        ChatMeters meters = registerChatMeters(agentIdTag);
        meters.counter().increment();
        meters.timer().record(elapsedNanos(startNanos), TimeUnit.NANOSECONDS);
    }

    public void recordLlmCall(String provider, String model, boolean success, long startNanos) {
        String providerTag = tagValue(provider);
        String modelTag = tagValue(model);
        String resultTag = success ? "success" : "failure";
        LlmMeters meters = registerLlmMeters(providerTag, modelTag, resultTag);
        meters.calls().increment();
        meters.results().increment();
        meters.timer().record(elapsedNanos(startNanos), TimeUnit.NANOSECONDS);
    }

    public void recordMcpToolCall(Long serverId, String toolName, boolean success) {
        String serverIdTag = tagValue(serverId);
        String toolNameTag = tagValue(toolName);
        String resultTag = success ? "success" : "failure";
        McpToolMeters meters = registerMcpToolMeters(serverIdTag, toolNameTag, resultTag);
        meters.calls().increment();
        meters.results().increment();
    }

    public void registerProviderCircuitBreaker(String provider, CircuitBreaker circuitBreaker) {
        String providerTag = tagValue(provider);
        for (CircuitBreaker.State state : new CircuitBreaker.State[]{
                CircuitBreaker.State.CLOSED,
                CircuitBreaker.State.OPEN,
                CircuitBreaker.State.HALF_OPEN
        }) {
            String key = providerTag + ":" + state.name();
            if (!circuitBreakerMeters.add(key)) {
                continue;
            }
            Gauge.builder("hify_provider_circuit_breaker_state", circuitBreaker,
                            breaker -> breaker.getState() == state ? 1.0 : 0.0)
                    .description("Provider circuit breaker state, 1 means current state")
                    .tag("provider", providerTag)
                    .tag("state", state.name())
                    .strongReference(true)
                    .register(meterRegistry);
        }
    }

    private ChatMeters registerChatMeters(String agentIdTag) {
        Counter counter = Counter.builder("hify_chat_requests")
                .description("Total chat requests")
                .tag("agentId", agentIdTag)
                .register(meterRegistry);
        Timer timer = Timer.builder("hify_chat_request_latency")
                .description("Chat request latency")
                .tag("agentId", agentIdTag)
                .publishPercentileHistogram()
                .register(meterRegistry);
        return new ChatMeters(counter, timer);
    }

    private LlmMeters registerLlmMeters(String providerTag, String modelTag, String resultTag) {
        Counter calls = Counter.builder("hify_llm_calls")
                .description("Total LLM calls")
                .tag("provider", providerTag)
                .tag("model", modelTag)
                .register(meterRegistry);
        Counter results = Counter.builder("hify_llm_call_results")
                .description("LLM call results")
                .tag("provider", providerTag)
                .tag("model", modelTag)
                .tag("result", resultTag)
                .register(meterRegistry);
        Timer timer = Timer.builder("hify_llm_call_latency")
                .description("LLM call latency")
                .tag("provider", providerTag)
                .tag("model", modelTag)
                .tag("result", resultTag)
                .publishPercentileHistogram()
                .register(meterRegistry);
        return new LlmMeters(calls, results, timer);
    }

    private McpToolMeters registerMcpToolMeters(String serverIdTag, String toolNameTag, String resultTag) {
        Counter calls = Counter.builder("hify_mcp_tool_calls")
                .description("Total MCP tool calls")
                .tag("serverId", serverIdTag)
                .tag("toolName", toolNameTag)
                .register(meterRegistry);
        Counter results = Counter.builder("hify_mcp_tool_call_results")
                .description("MCP tool call results")
                .tag("serverId", serverIdTag)
                .tag("toolName", toolNameTag)
                .tag("result", resultTag)
                .register(meterRegistry);
        return new McpToolMeters(calls, results);
    }

    private long elapsedNanos(long startNanos) {
        return Math.max(System.nanoTime() - startNanos, 0L);
    }

    private String tagValue(Long value) {
        return value != null ? value.toString() : UNKNOWN;
    }

    private String tagValue(String value) {
        return StringUtils.hasText(value) ? value : UNKNOWN;
    }

    private record ChatMeters(Counter counter, Timer timer) {
    }

    private record LlmMeters(Counter calls, Counter results, Timer timer) {
    }

    private record McpToolMeters(Counter calls, Counter results) {
    }
}
