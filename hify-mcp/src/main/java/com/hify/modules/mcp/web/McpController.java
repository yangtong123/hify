package com.hify.modules.mcp.web;

import com.hify.common.web.Result;
import com.hify.modules.mcp.api.McpService;
import com.hify.modules.mcp.api.dto.McpServerDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpController {

    private final McpService mcpService;

    @GetMapping("/servers")
    public Result<List<McpServerDto>> listServers(@RequestParam(required = false) Integer enabled) {
        return Result.ok(mcpService.listServers(enabled));
    }
}
