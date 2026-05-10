package com.hify.common.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DemoItemUpdateRequest {

    @NotNull(message = "ID不能为空")
    private Long id;

    @NotBlank(message = "名称不能为空")
    private String name;

    @NotNull(message = "状态不能为空")
    private Integer status;
}
