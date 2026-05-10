package com.hify.common.demo.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DemoItemResponse {

    private Long id;
    private String name;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
