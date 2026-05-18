package com.hify.modules.mcp.api.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class McpServerDetailResponse extends McpServerResponse {

    private List<McpToolResponse> tools;
}
