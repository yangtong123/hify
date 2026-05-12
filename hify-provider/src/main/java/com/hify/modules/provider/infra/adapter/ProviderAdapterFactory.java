package com.hify.modules.provider.infra.adapter;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ProviderAdapterFactory {

    private final Map<String, ProviderAdapter> adapterMap;

    public ProviderAdapterFactory(List<ProviderAdapter> adapters) {
        this.adapterMap = adapters.stream()
                .collect(Collectors.toMap(
                        a -> a.getType().toLowerCase(),
                        Function.identity()));
    }

    public ProviderAdapter getAdapter(String type) {
        ProviderAdapter adapter = adapterMap.get(type.toLowerCase());
        if (adapter == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的提供商类型: " + type);
        }
        return adapter;
    }
}
