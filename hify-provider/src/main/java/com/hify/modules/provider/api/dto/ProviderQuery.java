package com.hify.modules.provider.api.dto;

import lombok.Data;

@Data
public class ProviderQuery {

    private String type;
    private Integer enabled;
    private Integer page = 1;
    private Integer size = 20;
}
