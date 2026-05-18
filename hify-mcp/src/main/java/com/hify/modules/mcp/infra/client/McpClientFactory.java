package com.hify.modules.mcp.infra.client;

import com.hify.modules.mcp.infra.po.McpServerPo;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpClientFactory {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final ObjectMapper objectMapper;

    public McpSyncClient create(McpServerPo server) {
        Endpoint endpoint = Endpoint.parse(server.getUrl());
        boolean sse = isSse(server, endpoint);
        log.info("MCP client transport selected: serverId={}, endpoint={}, transport={}",
                server.getId(), server.getUrl(), sse ? "sse" : "streamable_http");
        McpClientTransport transport = sse
                ? createSseTransport(server, endpoint)
                : createStreamableHttpTransport(server, endpoint);
        return McpClient.sync(transport)
                .requestTimeout(REQUEST_TIMEOUT)
                .initializationTimeout(REQUEST_TIMEOUT)
                .clientInfo(new McpSchema.Implementation("hify-mcp-client", "1.0.0"))
                .build();
    }

    private McpClientTransport createStreamableHttpTransport(McpServerPo server, Endpoint endpoint) {
        HttpClientStreamableHttpTransport.Builder builder = HttpClientStreamableHttpTransport
                .builder(endpoint.baseUrl())
                .endpoint(endpoint.path())
                .jsonMapper(mcpJsonMapper());
        if (StringUtils.hasText(server.getApiKey())) {
            builder.requestBuilder(HttpRequest.newBuilder()
                    .header("Authorization", "Bearer " + server.getApiKey()));
        }
        builder.customizeClient(client -> client.connectTimeout(CONNECT_TIMEOUT));

        return builder.build();
    }

    private McpClientTransport createSseTransport(McpServerPo server, Endpoint endpoint) {
        HttpClientSseClientTransport.Builder builder = HttpClientSseClientTransport
                .builder(endpoint.baseUrl())
                .sseEndpoint(endpoint.path())
                .connectTimeout(CONNECT_TIMEOUT)
                .jsonMapper(mcpJsonMapper());
        if (StringUtils.hasText(server.getApiKey())) {
            builder.requestBuilder(HttpRequest.newBuilder()
                    .header("Authorization", "Bearer " + server.getApiKey()));
        }
        builder.customizeClient(client -> client.connectTimeout(CONNECT_TIMEOUT));

        return builder.build();
    }

    private McpJsonMapper mcpJsonMapper() {
        return new Jackson2McpJsonMapper(objectMapper);
    }

    private boolean isSse(McpServerPo server, Endpoint endpoint) {
        return "sse".equalsIgnoreCase(server.getServerType())
                || endpoint.path().startsWith("/sse")
                || endpoint.path().contains("/sse?");
    }

    private record Endpoint(String baseUrl, String path) {

        private static Endpoint parse(String endpoint) {
            URI uri = URI.create(endpoint);
            String baseUrl = uri.getScheme() + "://" + uri.getAuthority();
            String path = uri.getRawPath();
            if (!StringUtils.hasText(path) || "/".equals(path)) {
                path = "/mcp";
            }
            if (StringUtils.hasText(uri.getRawQuery())) {
                path = path + "?" + uri.getRawQuery();
            }
            return new Endpoint(baseUrl, path);
        }
    }
}
