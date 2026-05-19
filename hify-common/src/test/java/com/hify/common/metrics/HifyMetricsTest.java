package com.hify.common.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class HifyMetricsTest {

    @Test
    void recordChatRequestShouldReuseMetersForSameAgentId() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        HifyMetrics metrics = new HifyMetrics(meterRegistry);
        ReflectionTestUtils.setField(metrics, "maxAgentTags", 500);

        metrics.recordChatRequest(1L, System.nanoTime());
        metrics.recordChatRequest(1L, System.nanoTime());

        assertThat(meterRegistry.get("hify_chat_requests")
                .tag("agentId", "1")
                .counter()
                .count()).isEqualTo(2.0);
        assertThat(meterRegistry.find("hify_chat_requests").meters()).hasSize(1);
    }

    @Test
    void recordChatRequestShouldCollapseAgentIdsAfterLimit() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        HifyMetrics metrics = new HifyMetrics(meterRegistry);
        ReflectionTestUtils.setField(metrics, "maxAgentTags", 1);

        metrics.recordChatRequest(1L, System.nanoTime());
        metrics.recordChatRequest(2L, System.nanoTime());
        metrics.recordChatRequest(3L, System.nanoTime());

        assertThat(meterRegistry.get("hify_chat_requests")
                .tag("agentId", "1")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("hify_chat_requests")
                .tag("agentId", "overflow")
                .counter()
                .count()).isEqualTo(2.0);
    }
}
