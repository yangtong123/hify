package com.hify.common.log;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

    private static final String TRACE_LISTENER_ATTACHED_KEY = RequestTraceFilter.class.getName() + ".listenerAttached";
    private static final long SLOW_THRESHOLD_MS = 1000L;

    private final Tracer tracer;

    public RequestTraceFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String path = request.getRequestURI();
        Span span = tracer.spanBuilder(method + " " + path)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("http.request.method", method)
                .setAttribute("url.path", path)
                .startSpan();
        String traceId = span.getSpanContext().getTraceId();

        try (Scope ignored = span.makeCurrent()) {
            TraceContext.putTraceId(traceId);
            response.setHeader("X-Trace-Id", traceId);
            log.info("Request entered: method={}, path={}, query={}, remote={}",
                    method, path, request.getQueryString(), request.getRemoteAddr());
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            markError(span, exception);
            throw exception;
        } finally {
            if (request.isAsyncStarted()) {
                attachAsyncCompletionListener(request, response, span, startTime);
            } else {
                logCompletion(request, response, span, startTime);
                span.end();
            }
            TraceContext.clearTraceId();
        }
    }

    private void attachAsyncCompletionListener(HttpServletRequest request,
                                               HttpServletResponse response,
                                               Span span,
                                               long startTime) {
        if (Boolean.TRUE.equals(request.getAttribute(TRACE_LISTENER_ATTACHED_KEY))) {
            return;
        }
        request.setAttribute(TRACE_LISTENER_ATTACHED_KEY, true);
        AtomicBoolean completed = new AtomicBoolean(false);
        request.getAsyncContext().addListener(new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent event) {
                completeAsync(event, null);
            }

            @Override
            public void onTimeout(AsyncEvent event) {
                completeAsync(event, event.getThrowable());
            }

            @Override
            public void onError(AsyncEvent event) {
                completeAsync(event, event.getThrowable());
            }

            @Override
            public void onStartAsync(AsyncEvent event) {
            }

            private void completeAsync(AsyncEvent event, Throwable throwable) {
                if (!completed.compareAndSet(false, true)) {
                    return;
                }
                try (Scope ignored = span.makeCurrent()) {
                    TraceContext.putTraceId(span.getSpanContext().getTraceId());
                    if (throwable != null) {
                        markError(span, throwable);
                    }
                    HttpServletResponse asyncResponse = event.getSuppliedResponse() instanceof HttpServletResponse supplied
                            ? supplied : response;
                    logCompletion(request, asyncResponse, span, startTime);
                } finally {
                    span.end();
                    TraceContext.clearTraceId();
                }
            }
        });
    }

    private void logCompletion(HttpServletRequest request,
                               HttpServletResponse response,
                               Span span,
                               long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        int status = response.getStatus();
        span.setAttribute("http.response.status_code", status);
        span.setAttribute("request.duration_ms", duration);
        if (status >= 500) {
            span.setStatus(StatusCode.ERROR);
        }

        if (duration > SLOW_THRESHOLD_MS) {
            log.warn("Request completed: method={}, path={}, status={}, latency={}ms, slow=true",
                    request.getMethod(), request.getRequestURI(), status, duration);
        } else {
            log.info("Request completed: method={}, path={}, status={}, latency={}ms",
                    request.getMethod(), request.getRequestURI(), status, duration);
        }
    }

    private void markError(Span span, Throwable throwable) {
        span.setStatus(StatusCode.ERROR);
        if (throwable != null) {
            span.recordException(throwable);
        }
    }
}
