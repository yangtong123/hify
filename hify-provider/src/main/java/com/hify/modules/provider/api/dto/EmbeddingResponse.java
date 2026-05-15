package com.hify.modules.provider.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class EmbeddingResponse {

    private String model;

    private List<List<Double>> embeddings;
}
