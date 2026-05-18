package com.hify.modules.mcp.web;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.modules.mcp.api.McpService;
import com.hify.modules.mcp.api.McpClientService;
import com.hify.modules.mcp.api.dto.McpConnectionTestResult;
import com.hify.modules.mcp.api.dto.McpServerDetailResponse;
import com.hify.modules.mcp.api.dto.McpServerDto;
import com.hify.modules.mcp.api.dto.McpServerQuery;
import com.hify.modules.mcp.api.dto.McpServerRequest;
import com.hify.modules.mcp.api.dto.McpServerResponse;
import com.hify.modules.mcp.api.dto.McpToolDebugRequest;
import com.hify.modules.mcp.api.dto.McpToolDebugResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/mcp-servers", "/mcp/servers"})
@RequiredArgsConstructor
public class McpController {

    private final McpService mcpService;
    private final McpClientService mcpClientService;

    @PostMapping
    public Result<McpServerResponse> create(@Valid @RequestBody McpServerRequest request) {
        return Result.ok(mcpService.create(request));
    }

    @GetMapping
    public PageResult<List<McpServerResponse>> list(McpServerQuery query) {
        return mcpService.list(query);
    }

    @GetMapping("/{id}")
    public Result<McpServerDetailResponse> getById(@PathVariable Long id) {
        return Result.ok(mcpService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<McpServerResponse> update(@PathVariable Long id, @Valid @RequestBody McpServerRequest request) {
        return Result.ok(mcpService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mcpService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/test")
    public Result<McpConnectionTestResult> testConnection(@PathVariable Long id) {
        return Result.ok(mcpService.testConnection(id));
    }

    @PostMapping("/{id}/debug")
    public Result<McpToolDebugResponse> debugTool(@PathVariable Long id,
                                                  @Valid @RequestBody McpToolDebugRequest request) {
        long start = System.currentTimeMillis();
        try {
            String result = mcpClientService.callTool(id, request.getToolName(), request.getArguments());
            return Result.ok(new McpToolDebugResponse(result, elapsedMs(start)));
        } catch (RuntimeException | LinkageError e) {
            String message = e.getMessage() != null ? e.getMessage() : "MCP 工具调用失败";
            return Result.ok(new McpToolDebugResponse("{\"error\":\"" + escapeJson(message) + "\"}", elapsedMs(start)));
        }
    }

    @GetMapping("/available")
    public Result<List<McpServerDto>> listAvailableServers(@RequestParam(required = false) Integer enabled) {
        return Result.ok(mcpService.listServers(enabled));
    }

    private Integer elapsedMs(long start) {
        long elapsed = System.currentTimeMillis() - start;
        return elapsed > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) elapsed;
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
