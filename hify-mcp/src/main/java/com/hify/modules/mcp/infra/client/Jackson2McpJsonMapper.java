package com.hify.modules.mcp.infra.client;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import lombok.RequiredArgsConstructor;

import java.io.IOException;

@RequiredArgsConstructor
class Jackson2McpJsonMapper implements McpJsonMapper {

    private final ObjectMapper objectMapper;

    @Override
    public <T> T readValue(String content, Class<T> type) throws IOException {
        return objectMapper.readValue(content, type);
    }

    @Override
    public <T> T readValue(byte[] content, Class<T> type) throws IOException {
        return objectMapper.readValue(content, type);
    }

    @Override
    public <T> T readValue(String content, TypeRef<T> typeRef) throws IOException {
        return objectMapper.readValue(content, toJavaType(typeRef));
    }

    @Override
    public <T> T readValue(byte[] content, TypeRef<T> typeRef) throws IOException {
        return objectMapper.readValue(content, toJavaType(typeRef));
    }

    @Override
    public <T> T convertValue(Object value, Class<T> type) {
        return objectMapper.convertValue(value, type);
    }

    @Override
    public <T> T convertValue(Object value, TypeRef<T> typeRef) {
        return objectMapper.convertValue(value, toJavaType(typeRef));
    }

    @Override
    public String writeValueAsString(Object value) throws IOException {
        return objectMapper.writeValueAsString(value);
    }

    @Override
    public byte[] writeValueAsBytes(Object value) throws IOException {
        return objectMapper.writeValueAsBytes(value);
    }

    private JavaType toJavaType(TypeRef<?> typeRef) {
        return objectMapper.getTypeFactory().constructType(typeRef.getType());
    }
}
