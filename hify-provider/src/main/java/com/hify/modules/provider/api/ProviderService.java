package com.hify.modules.provider.api;

import com.hify.common.web.PageResult;
import com.hify.modules.provider.api.dto.ConnectionTestResult;
import com.hify.modules.provider.api.dto.ProviderDetailResponse;
import com.hify.modules.provider.api.dto.ProviderQuery;
import com.hify.modules.provider.api.dto.ProviderRequest;
import com.hify.modules.provider.api.dto.ProviderResponse;

import java.util.List;

public interface ProviderService {

    ProviderResponse create(ProviderRequest request);

    ProviderResponse update(Long id, ProviderRequest request);

    void delete(Long id);

    ProviderDetailResponse getById(Long id);

    PageResult<List<ProviderResponse>> list(ProviderQuery query);

    ConnectionTestResult testConnection(Long id);
}
