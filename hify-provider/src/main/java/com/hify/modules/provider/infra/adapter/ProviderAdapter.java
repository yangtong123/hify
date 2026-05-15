package com.hify.modules.provider.infra.adapter;

import com.hify.modules.provider.api.dto.ConnectionTestResult;
import com.hify.modules.provider.api.dto.ChatRequest;
import com.hify.modules.provider.api.dto.ChatResponse;
import com.hify.modules.provider.api.dto.EmbeddingRequest;
import com.hify.modules.provider.api.dto.EmbeddingResponse;
import com.hify.modules.provider.infra.po.ProviderPo;

import java.util.List;
import java.util.function.Consumer;

public interface ProviderAdapter {

    String getType();

    ConnectionTestResult testConnection(ProviderPo provider);

    List<String> listModels(ProviderPo provider);

    ChatResponse chat(ProviderPo provider, ChatRequest request);

    void streamChat(ProviderPo provider, ChatRequest request, Consumer<ChatResponse> chunkConsumer);

    default EmbeddingResponse embed(ProviderPo provider, EmbeddingRequest request) {
        throw new UnsupportedOperationException("Embedding is not supported by provider type: " + getType());
    }
}
