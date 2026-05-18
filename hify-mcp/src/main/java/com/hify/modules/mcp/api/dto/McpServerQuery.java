package com.hify.modules.mcp.api.dto;

import lombok.Data;

@Data
public class McpServerQuery {

    private String name;

    private Integer enabled;

    private Integer page = 1;

    private Integer size = 20;
}
