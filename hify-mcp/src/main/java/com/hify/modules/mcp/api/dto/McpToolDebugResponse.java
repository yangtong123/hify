package com.hify.modules.mcp.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpToolDebugResponse {

    private String result;

    private Integer elapsedMs;
}
