package com.hify.modules.provider.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class EmbeddingRequest {

    private String model;

    private List<String> inputs;
}
