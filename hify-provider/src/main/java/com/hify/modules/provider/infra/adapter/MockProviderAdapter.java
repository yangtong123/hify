package com.hify.modules.provider.infra.adapter;

import com.hify.modules.provider.api.dto.ChatRequest;
import com.hify.modules.provider.api.dto.ChatResponse;
import com.hify.modules.provider.api.dto.ConnectionTestResult;
import com.hify.modules.provider.infra.po.ProviderPo;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Component
@Profile("mock")
public class MockProviderAdapter implements ProviderAdapter {

    @Override
    public String getType() {
        return "mock";
    }

    @Override
    public ConnectionTestResult testConnection(ProviderPo provider) {
        return ConnectionTestResult.success(3L, 1);
    }

    @Override
    public List<String> listModels(ProviderPo provider) {
        return List.of("gpt-mock");
    }

    @Override
    public ChatResponse chat(ProviderPo provider, ChatRequest request) {
        ChatResponse response = baseResponse(request);
        response.setContent(replyContent(request));
        response.setFinishReason("stop");
        response.setUsage(usage(response.getContent()));
        return response;
    }

    @Override
    public void streamChat(ProviderPo provider, ChatRequest request, Consumer<ChatResponse> chunkConsumer) {
        String content = replyContent(request);
        int midpoint = Math.max(1, content.length() / 2);
        chunkConsumer.accept(ChatResponse.delta(content.substring(0, midpoint)));
        chunkConsumer.accept(ChatResponse.delta(content.substring(midpoint)));

        ChatResponse done = baseResponse(request);
        done.setFinishReason("stop");
        done.setUsage(usage(content));
        chunkConsumer.accept(done);
    }

    private ChatResponse baseResponse(ChatRequest request) {
        ChatResponse response = new ChatResponse();
        response.setId("mock-" + UUID.randomUUID());
        response.setModel(request.getModel());
        response.setRole("assistant");
        return response;
    }

    private String replyContent(ChatRequest request) {
        String userContent = "";
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            for (int i = request.getMessages().size() - 1; i >= 0; i--) {
                ChatRequest.Message message = request.getMessages().get(i);
                if ("user".equals(message.getRole())) {
                    userContent = message.getContent();
                    break;
                }
            }
        }
        return "Mock response: " + userContent;
    }

    private ChatResponse.Usage usage(String content) {
        ChatResponse.Usage usage = new ChatResponse.Usage();
        usage.setPromptTokens(8);
        usage.setCompletionTokens(Math.max(1, content.length() / 4));
        usage.setTotalTokens(usage.getPromptTokens() + usage.getCompletionTokens());
        return usage;
    }
}
