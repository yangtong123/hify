package com.hify.modules.provider.web;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.modules.provider.api.ProviderService;
import com.hify.modules.provider.api.dto.ConnectionTestResult;
import com.hify.modules.provider.api.dto.ProviderDetailResponse;
import com.hify.modules.provider.api.dto.ProviderQuery;
import com.hify.modules.provider.api.dto.ProviderRequest;
import com.hify.modules.provider.api.dto.ProviderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "提供商管理", description = "LLM 提供商的 CRUD 和连通性测试")
@RestController
@RequestMapping("/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService providerService;

    @Operation(summary = "创建提供商", description = "创建新的 LLM 提供商配置，名称不可重复")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "创建成功"),
            @ApiResponse(responseCode = "422", description = "参数校验失败或名称重复", content = @Content)
    })
    @PostMapping
    public Result<ProviderResponse> create(
            @Parameter(description = "提供商创建请求", required = true)
            @Valid @RequestBody ProviderRequest request) {
        return Result.ok(providerService.create(request));
    }

    @Operation(summary = "更新提供商", description = "根据 ID 更新提供商配置")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "更新成功"),
            @ApiResponse(responseCode = "404", description = "提供商不存在", content = @Content),
            @ApiResponse(responseCode = "422", description = "参数校验失败或名称重复", content = @Content)
    })
    @PutMapping("/{id}")
    public Result<ProviderResponse> update(
            @Parameter(description = "提供商 ID", required = true, example = "1")
            @PathVariable Long id,
            @Parameter(description = "提供商更新请求", required = true)
            @Valid @RequestBody ProviderRequest request) {
        return Result.ok(providerService.update(id, request));
    }

    @Operation(summary = "删除提供商", description = "根据 ID 软删除提供商")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "删除成功"),
            @ApiResponse(responseCode = "404", description = "提供商不存在", content = @Content)
    })
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "提供商 ID", required = true, example = "1")
            @PathVariable Long id) {
        providerService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "查询提供商详情", description = "根据 ID 查询提供商详情，包含关联的模型配置列表和健康状态")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功",
                    content = @Content(schema = @Schema(implementation = ProviderDetailResponse.class))),
            @ApiResponse(responseCode = "404", description = "提供商不存在", content = @Content)
    })
    @GetMapping("/{id}")
    public Result<ProviderDetailResponse> getById(
            @Parameter(description = "提供商 ID", required = true, example = "1")
            @PathVariable Long id) {
        return Result.ok(providerService.getById(id));
    }

    @Operation(summary = "查询提供商列表", description = "分页查询提供商列表，支持按类型和启用状态筛选")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功")
    })
    @GetMapping
    public Result<List<ProviderResponse>> list(
            @Parameter(description = "查询条件：providerType、isEnabled、page、size")
            ProviderQuery query) {
        return providerService.list(query);
    }

    @Operation(summary = "连通性测试", description = "对指定提供商发起连通性测试，根据 providerType 分发到不同的测试端点")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "测试完成（成功或失败均返回 200，通过 success 字段判断）",
                    content = @Content(schema = @Schema(implementation = ConnectionTestResult.class))),
            @ApiResponse(responseCode = "404", description = "提供商不存在", content = @Content)
    })
    @PostMapping("/{id}/test-connection")
    public Result<ConnectionTestResult> testConnection(
            @Parameter(description = "提供商 ID", required = true, example = "1")
            @PathVariable Long id) {
        return Result.ok(providerService.testConnection(id));
    }
}
