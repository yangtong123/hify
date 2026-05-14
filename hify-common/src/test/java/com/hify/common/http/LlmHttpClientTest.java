package com.hify.common.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmHttpClientTest {

    @Test
    void streamShouldDispatchLinesAsUpstreamFlushes() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/stream", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                writeAndFlush(outputStream, "data: first\n\n");
                sleep(250);
                writeAndFlush(outputStream, "data: second\n\n");
                sleep(250);
                writeAndFlush(outputStream, "data: third\n\n");
            }
        });
        server.start();

        try {
            LlmHttpClient client = new LlmHttpClient();
            List<String> lines = new ArrayList<>();
            List<Long> arrivalTimes = new ArrayList<>();
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/stream";

            client.stream(url, null, "{}", line -> {
                lines.add(line);
                arrivalTimes.add(System.nanoTime());
            });

            assertThat(lines).containsExactly("data: first", "data: second", "data: third");
            assertThat(elapsedMillis(arrivalTimes.get(0), arrivalTimes.get(1))).isGreaterThanOrEqualTo(150);
            assertThat(elapsedMillis(arrivalTimes.get(1), arrivalTimes.get(2))).isGreaterThanOrEqualTo(150);
        } finally {
            server.stop(0);
        }
    }

    private static void writeAndFlush(OutputStream outputStream, String value) throws IOException {
        outputStream.write(value.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("stream test interrupted", e);
        }
    }

    private static long elapsedMillis(long startNanos, long endNanos) {
        return (endNanos - startNanos) / 1_000_000;
    }
}
