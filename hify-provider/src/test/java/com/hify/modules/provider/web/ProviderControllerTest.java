package com.hify.modules.provider.web;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.modules.provider.api.ProviderService;
import com.hify.modules.provider.api.dto.ConnectionTestResult;
import com.hify.modules.provider.api.dto.ProviderDetailResponse;
import com.hify.modules.provider.api.dto.ProviderQuery;
import com.hify.modules.provider.api.dto.ProviderRequest;
import com.hify.modules.provider.api.dto.ProviderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderControllerTest {

    @Mock
    private ProviderService providerService;

    private ProviderController controller;

    @BeforeEach
    void setUp() {
        controller = new ProviderController(providerService);
    }

    @Test
    void createShouldWrapServiceResponse() {
        ProviderRequest request = request();
        ProviderResponse response = response(1L, "OpenAI");
        when(providerService.create(request)).thenReturn(response);

        Result<ProviderResponse> result = controller.create(request);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMessage()).isEqualTo("success");
        assertThat(result.getData()).isSameAs(response);
    }

    @Test
    void updateShouldDelegateToService() {
        ProviderRequest request = request();
        ProviderResponse response = response(2L, "Azure OpenAI");
        when(providerService.update(2L, request)).thenReturn(response);

        Result<ProviderResponse> result = controller.update(2L, request);

        assertThat(result.getData()).isSameAs(response);
        verify(providerService).update(2L, request);
    }

    @Test
    void deleteShouldReturnEmptySuccessResult() {
        Result<Void> result = controller.delete(3L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNull();
        verify(providerService).delete(3L);
    }

    @Test
    void getByIdShouldWrapDetailResponse() {
        ProviderDetailResponse detail = new ProviderDetailResponse();
        detail.setId(4L);
        when(providerService.getById(4L)).thenReturn(detail);

        Result<ProviderDetailResponse> result = controller.getById(4L);

        assertThat(result.getData()).isSameAs(detail);
    }

    @Test
    void listShouldPassQueryToService() {
        ProviderQuery query = new ProviderQuery();
        List<ProviderResponse> responses = List.of(response(1L, "OpenAI"));
        when(providerService.list(query)).thenReturn(PageResult.ok(responses, 1L, 1, 20));

        Result<List<ProviderResponse>> result = controller.list(query);

        assertThat(result.getData()).isSameAs(responses);
    }

    @Test
    void testConnectionShouldWrapConnectionResult() {
        ConnectionTestResult connection = ConnectionTestResult.success(12, 3);
        when(providerService.testConnection(1L)).thenReturn(connection);

        Result<ConnectionTestResult> result = controller.testConnection(1L);

        assertThat(result.getData()).isSameAs(connection);
    }

    private ProviderRequest request() {
        ProviderRequest request = new ProviderRequest();
        request.setName("OpenAI");
        request.setType("openai");
        request.setBaseUrl("https://api.openai.com");
        request.setEnabled(1);
        return request;
    }

    private ProviderResponse response(Long id, String name) {
        ProviderResponse response = new ProviderResponse();
        response.setId(id);
        response.setName(name);
        response.setType("openai");
        response.setBaseUrl("https://api.openai.com");
        response.setEnabled(1);
        return response;
    }
}
