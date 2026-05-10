package com.hify.common.util;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.web.PageResult;

import java.util.List;

public final class PageHelper {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private PageHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static <T> Page<T> toPage(Integer page, Integer pageSize) {
        int pageNum = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        return new Page<>(pageNum, size);
    }

    public static <T> PageResult<List<T>> toPageResult(com.baomidou.mybatisplus.core.metadata.IPage<T> iPage) {
        return PageResult.ok(
                iPage.getRecords(),
                iPage.getTotal(),
                (int) iPage.getCurrent(),
                (int) iPage.getSize()
        );
    }
}
