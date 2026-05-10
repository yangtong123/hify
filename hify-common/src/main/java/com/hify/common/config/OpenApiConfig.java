package com.hify.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI hifyOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hify API")
                        .description("简化版内部 AI Agent 平台")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("yangtong")
                                .email("yangtong@example.com")));
    }
}
