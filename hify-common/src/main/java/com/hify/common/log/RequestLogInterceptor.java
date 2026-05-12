package com.hify.common.log;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Slf4j
public class RequestLogInterceptor implements HandlerInterceptor {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String START_TIME_KEY = "requestStartTime";
    private static final long SLOW_THRESHOLD_MS = 1000L;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(TRACE_ID_KEY, traceId);
        request.setAttribute(START_TIME_KEY, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            Long startTime = (Long) request.getAttribute(START_TIME_KEY);
            if (startTime == null) {
                return;
            }
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();
            String method = request.getMethod();
            String path = request.getRequestURI();

            if (duration > SLOW_THRESHOLD_MS) {
                log.warn("{} {} -> {} ({}ms) [SLOW]", method, path, status, duration);
            } else {
                log.info("{} {} -> {} ({}ms)", method, path, status, duration);
            }
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }
}
