package com.hify.modules.provider.infra.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmHttpClient;
import org.springframework.stereotype.Component;

@Component
public class OpenAiCompatibleAdapter extends OpenAiAdapter {

    public OpenAiCompatibleAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
        super(llmHttpClient, objectMapper);
    }

    @Override
    public String getType() {
        return "openai_compatible";
    }
}
