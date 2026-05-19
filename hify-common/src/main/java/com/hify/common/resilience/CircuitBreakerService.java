package com.hify.common.resilience;

import com.hify.common.exception.ErrorCode;
import com.hify.common.exception.BizException;
import com.hify.common.exception.LlmApiException;
import com.hify.common.exception.LlmApiException.ErrorType;
import com.hify.common.metrics.HifyMetrics;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitBreakerService {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final HifyMetrics hifyMetrics;

    public CircuitBreaker getOrCreate(String providerName) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(providerName);
        hifyMetrics.registerProviderCircuitBreaker(providerName, circuitBreaker);
        return circuitBreaker;
    }

    public <T> T execute(String providerName, Supplier<T> call) {
        CircuitBreaker cb = getOrCreate(providerName);
        return executeWithRetry(cb, call);
    }

    public <T> T executeWithoutRetry(String providerName, Supplier<T> call) {
        CircuitBreaker cb = getOrCreate(providerName);
        try {
            return cb.executeSupplier(call);
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker open for [{}], call rejected", cb.getName());
            throw new BizException(ErrorCode.THIRD_PARTY_ERROR, "Provider temporarily unavailable");
        }
    }

    private <T> T executeWithRetry(CircuitBreaker cb, Supplier<T> call) {
        try {
            return cb.executeSupplier(call);
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker open for [{}], call rejected", cb.getName());
            throw new BizException(ErrorCode.THIRD_PARTY_ERROR, "Provider temporarily unavailable");
        } catch (LlmApiException e) {
            return switch (e.getErrorType()) {
                case TIMEOUT -> retryFixedDelay(cb, call, 2, 1000L, ErrorType.TIMEOUT);
                case RATE_LIMITED -> retryBackoff(cb, call, new long[]{2000L, 4000L}, ErrorType.RATE_LIMITED);
                case AUTH_FAILED, API_ERROR -> throw e;
            };
        }
    }

    private <T> T retryFixedDelay(CircuitBreaker cb, Supplier<T> call, int maxRetries,
                                   long delayMs, ErrorType retryOn) {
        LlmApiException lastException = null;
        for (int i = 0; i < maxRetries; i++) {
            try {
                log.info("Retry {} (fixed {}ms) for [{}]", i + 1, delayMs, cb.getName());
                Thread.sleep(delayMs);
                return cb.executeSupplier(call);
            } catch (LlmApiException e) {
                lastException = e;
                if (e.getErrorType() != retryOn) {
                    throw e;
                }
                log.warn("Retry {} failed for [{}]: {}", i + 1, cb.getName(), e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BizException(ErrorCode.INTERNAL_ERROR, "Retry interrupted");
            }
        }
        throw lastException;
    }

    private <T> T retryBackoff(CircuitBreaker cb, Supplier<T> call, long[] delaysMs,
                                ErrorType retryOn) {
        LlmApiException lastException = null;
        for (int i = 0; i < delaysMs.length; i++) {
            try {
                log.info("Retry {} (backoff {}ms) for [{}]", i + 1, delaysMs[i], cb.getName());
                Thread.sleep(delaysMs[i]);
                return cb.executeSupplier(call);
            } catch (LlmApiException e) {
                lastException = e;
                if (e.getErrorType() != retryOn) {
                    throw e;
                }
                log.warn("Retry {} failed for [{}]: {}", i + 1, cb.getName(), e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BizException(ErrorCode.INTERNAL_ERROR, "Retry interrupted");
            }
        }
        throw lastException;
    }
}
