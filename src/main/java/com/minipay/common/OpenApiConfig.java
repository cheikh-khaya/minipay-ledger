package com.minipay.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI minipayOpenApi() {
        return new OpenAPI().info(new Info()
                .title("minipay-ledger API")
                .version("0.1.0")
                .description("Accounts, deposits and idempotent money transfers backed by a simple ledger.")
                .license(new License().name("MIT")));
    }
}
