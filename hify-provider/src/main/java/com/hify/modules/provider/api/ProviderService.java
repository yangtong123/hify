package com.hify.modules.provider.api;

import com.hify.common.web.PageResult;
import com.hify.modules.provider.api.dto.ConnectionTestResult;
import com.hify.modules.provider.api.dto.ChatRequest;
import com.hify.modules.provider.api.dto.ChatResponse;
import com.hify.modules.provider.api.dto.EmbeddingRequest;
import com.hify.modules.provider.api.dto.EmbeddingResponse;
import com.hify.modules.provider.api.dto.ModelConfigDto;
import com.hify.modules.provider.api.dto.ProviderDetailResponse;
import com.hify.modules.provider.api.dto.ProviderQuery;
import com.hify.modules.provider.api.dto.ProviderRequest;
import com.hify.modules.provider.api.dto.ProviderResponse;

import java.util.List;
import java.util.function.Consumer;

public interface ProviderService {

    ProviderResponse create(ProviderRequest request);

    ProviderResponse update(Long id, ProviderRequest request);

    void delete(Long id);

    ProviderDetailResponse getById(Long id);

    PageResult<List<ProviderResponse>> list(ProviderQuery query);

    List<ModelConfigDto> listAvailableModelConfigs();

    ConnectionTestResult testConnection(Long id);

    ModelConfigDto getModelConfig(Long modelConfigId);

    List<ModelConfigDto> listModelConfigsByIds(List<Long> modelConfigIds);

    ChatResponse chat(Long modelConfigId, ChatRequest request);

    void streamChat(Long modelConfigId, ChatRequest request, Consumer<ChatResponse> chunkConsumer);

    EmbeddingResponse embed(Long modelConfigId, EmbeddingRequest request);
}
