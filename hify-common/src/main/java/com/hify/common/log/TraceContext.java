package com.hify.common.log;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.MDC;

import java.util.Map;

public final class TraceContext {

    public static final String TRACE_ID_KEY = "traceId";

    private TraceContext() {
    }

    public static Runnable wrap(Runnable delegate) {
        Map<String, String> capturedMdc = MDC.getCopyOfContextMap();
        Context capturedContext = Context.current();
        return () -> {
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();
            try (Scope ignored = capturedContext.makeCurrent()) {
                restoreMdc(capturedMdc);
                delegate.run();
            } finally {
                restoreMdc(previousMdc);
            }
        };
    }

    public static void putTraceId(String traceId) {
        MDC.put(TRACE_ID_KEY, traceId);
    }

    public static void clearTraceId() {
        MDC.remove(TRACE_ID_KEY);
    }

    private static void restoreMdc(Map<String, String> contextMap) {
        if (contextMap == null || contextMap.isEmpty()) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(contextMap);
    }
}
