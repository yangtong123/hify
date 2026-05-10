package com.hify.common.demo.web;

import com.hify.common.demo.dto.DemoItemCreateRequest;
import com.hify.common.demo.dto.DemoItemResponse;
import com.hify.common.demo.dto.DemoItemUpdateRequest;
import com.hify.common.demo.service.DemoItemService;
import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Demo 演示", description = "CRUD 演示接口")
@RestController
@RequestMapping("/demo-items")
@RequiredArgsConstructor
public class DemoItemController {

    private final DemoItemService service;

    @Operation(summary = "创建 Demo 项")
    @PostMapping
    public Result<DemoItemResponse> create(@Valid @RequestBody DemoItemCreateRequest request) {
        return Result.ok(service.create(request));
    }

    @Operation(summary = "更新 Demo 项")
    @PutMapping("/{id}")
    public Result<DemoItemResponse> update(
            @Parameter(description = "Demo 项 ID") @PathVariable Long id,
            @Valid @RequestBody DemoItemUpdateRequest request) {
        request.setId(id);
        return Result.ok(service.update(request));
    }

    @Operation(summary = "删除 Demo 项")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "Demo 项 ID") @PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @Operation(summary = "查询 Demo 项详情")
    @GetMapping("/{id}")
    public Result<DemoItemResponse> get(
            @Parameter(description = "Demo 项 ID") @PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    @Operation(summary = "分页查询 Demo 项列表")
    @GetMapping
    public PageResult<List<DemoItemResponse>> list(
            @Parameter(description = "页码，从 1 开始") @RequestParam(required = false) Integer page,
            @Parameter(description = "每页条数，默认 20，最大 100") @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        return service.list(page, pageSize);
    }
}
