package com.bancoias.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bancoiasOpenApi() {
        return new OpenAPI().info(new Info()
                .title("BancoIAS - Credit Applications API")
                .version("v1")
                .description("Procesamiento idempotente y concurrente de solicitudes de crédito."));
    }
}
