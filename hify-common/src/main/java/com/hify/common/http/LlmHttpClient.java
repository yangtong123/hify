package com.hify.common.http;

import com.hify.common.exception.LlmApiException;
import com.hify.common.exception.LlmApiException.ErrorType;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;

import java.util.function.Consumer;

@Slf4j
@Component
public class LlmHttpClient {

    private final RestTemplate restTemplate;
    private final OkHttpClient okHttpClient;

    public LlmHttpClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.restTemplate = new RestTemplate(factory);

        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(120))
                .build();
    }

    public String post(String url, Map<String, String> headers, String body) {
        long start = System.currentTimeMillis();
        int status = 0;
        log.info("LLM POST started: url={}, headers={}, bodyLength={}",
                url, headers != null ? headers.size() : 0, body != null ? body.length() : 0);
        try {
            HttpHeaders httpHeaders = new HttpHeaders();
            if (headers != null) {
                headers.forEach(httpHeaders::add);
            }
            httpHeaders.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(body, httpHeaders);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            status = response.getStatusCode().value();
            long duration = System.currentTimeMillis() - start;
            log.info("LLM POST {} -> {} ({}ms)", url, status, duration);

            if (status == 401 || status == 403) {
                throw new LlmApiException(ErrorType.AUTH_FAILED, status, "auth failed: " + status);
            }
            if (status == 429) {
                throw new LlmApiException(ErrorType.RATE_LIMITED, status, "rate limited");
            }
            if (status >= 400) {
                throw new LlmApiException(ErrorType.API_ERROR, status, "API error: " + status);
            }

            return response.getBody();
        } catch (LlmApiException e) {
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("LLM POST {} -> {} ({}ms): {}", url, status, duration, e.getMessage());
            throw mapException(e);
        }
    }

    public void stream(String url, Map<String, String> headers, String body, Consumer<String> callback) {
        long start = System.currentTimeMillis();
        int status = 0;
        int lineCount = 0;
        log.info("LLM STREAM started: url={}, headers={}, bodyLength={}",
                url, headers != null ? headers.size() : 0, body != null ? body.length() : 0);
        try {
            Request.Builder requestBuilder = new Request.Builder().url(url);
            if (headers != null) {
                headers.forEach(requestBuilder::addHeader);
            }
            requestBuilder.addHeader("Content-Type", "application/json");

            Request request = requestBuilder
                    .post(RequestBody.create(body, MediaType.parse("application/json")))
                    .build();

            Response response = okHttpClient.newCall(request).execute();
            status = response.code();

            if (status == 401 || status == 403) {
                response.close();
                throw new LlmApiException(ErrorType.AUTH_FAILED, status, "auth failed: " + status);
            }
            if (status == 429) {
                response.close();
                throw new LlmApiException(ErrorType.RATE_LIMITED, status, "rate limited");
            }
            if (status >= 400) {
                response.close();
                throw new LlmApiException(ErrorType.API_ERROR, status, "API error: " + status);
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                response.close();
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody.byteStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isEmpty()) {
                        lineCount++;
                        callback.accept(line);
                    }
                }
            }

            long duration = System.currentTimeMillis() - start;
            log.info("LLM STREAM {} -> {} ({}ms), lines={}", url, status, duration, lineCount);
        } catch (LlmApiException e) {
            throw e;
        } catch (RuntimeException e) {
            long duration = System.currentTimeMillis() - start;
            log.warn("LLM STREAM {} interrupted by callback ({}ms), lines={}: {}",
                    url, duration, lineCount, e.getMessage());
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("LLM STREAM {} -> {} ({}ms), lines={}: {}", url, status, duration, lineCount, e.getMessage());
            throw mapException(e);
        }
    }

    /**
     * Simple GET with configurable timeout. Used for connectivity tests.
     */
    public String get(String url, Map<String, String> headers, long timeoutSeconds) {
        long start = System.currentTimeMillis();
        log.info("LLM GET started: url={}, headers={}, timeoutSeconds={}",
                url, headers != null ? headers.size() : 0, timeoutSeconds);
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .readTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        Request.Builder builder = new Request.Builder().url(url).get();
        if (headers != null) {
            headers.forEach(builder::addHeader);
        }

        try (Response response = client.newCall(builder.build()).execute()) {
            int status = response.code();
            long duration = System.currentTimeMillis() - start;
            log.info("LLM GET {} -> {} ({}ms)", url, status, duration);
            if (status == 401 || status == 403) {
                throw new LlmApiException(ErrorType.AUTH_FAILED, status, "auth failed: " + status);
            }
            if (status >= 400) {
                throw new LlmApiException(ErrorType.API_ERROR, status, "API error: " + status);
            }
            ResponseBody body = response.body();
            return body != null ? body.string() : "";
        } catch (LlmApiException e) {
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("LLM GET {} failed ({}ms): {}", url, duration, e.getMessage());
            throw mapException(e);
        }
    }

    private LlmApiException mapException(Exception e) {
        if (e instanceof SocketTimeoutException) {
            return new LlmApiException(ErrorType.TIMEOUT, "LLM request timeout", e);
        }
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("timed out") || msg.contains("timeout")) {
            return new LlmApiException(ErrorType.TIMEOUT, msg, e);
        }
        if (msg.contains("401") || msg.contains("403") || msg.contains("Unauthorized")) {
            return new LlmApiException(ErrorType.AUTH_FAILED, msg, e);
        }
        if (msg.contains("429")) {
            return new LlmApiException(ErrorType.RATE_LIMITED, msg, e);
        }
        return new LlmApiException(ErrorType.API_ERROR, msg, e);
    }
}
