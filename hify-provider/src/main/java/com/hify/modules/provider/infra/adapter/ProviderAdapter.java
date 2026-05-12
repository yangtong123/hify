package com.hify.modules.provider.infra.adapter;

import com.hify.modules.provider.api.dto.ConnectionTestResult;
import com.hify.modules.provider.infra.po.ProviderPo;

import java.util.List;

public interface ProviderAdapter {

    String getType();

    ConnectionTestResult testConnection(ProviderPo provider);

    List<String> listModels(ProviderPo provider);
}
