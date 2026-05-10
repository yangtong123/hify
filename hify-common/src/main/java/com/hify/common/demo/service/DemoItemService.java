package com.hify.common.demo.service;

import com.hify.common.demo.dto.DemoItemCreateRequest;
import com.hify.common.demo.dto.DemoItemResponse;
import com.hify.common.demo.dto.DemoItemUpdateRequest;
import com.hify.common.web.PageResult;

import java.util.List;

public interface DemoItemService {

    DemoItemResponse create(DemoItemCreateRequest request);

    DemoItemResponse update(DemoItemUpdateRequest request);

    void delete(Long id);

    DemoItemResponse getById(Long id);

    PageResult<List<DemoItemResponse>> list(Integer page, Integer pageSize);
}
