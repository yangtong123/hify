package com.hify.modules.mcp.api.dto;

import lombok.Data;

@Data
public class McpServerDto {

    private Long id;

    private String name;

    private String description;

    private String serverType;

    private Integer enabled;
}
