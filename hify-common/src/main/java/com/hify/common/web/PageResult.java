package com.hify.common.web;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PageResult<T> extends Result<T> {

    private long total;
    private int page;
    private int size;

    private PageResult(int code, String message, T data, long total, int page, int size) {
        super(code, message, data);
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public static <T> PageResult<T> ok(T data, long total, int page, int size) {
        return new PageResult<>(200, "success", data, total, page, size);
    }
}
