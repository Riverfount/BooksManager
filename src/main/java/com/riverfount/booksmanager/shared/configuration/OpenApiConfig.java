package com.riverfount.booksmanager.shared.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * General API information (title, version, description) shown in the
 * Swagger UI, instead of springdoc's defaults (RNF24).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI booksManagerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Books Manager API")
                        .version("v1")
                        .description("API REST para gerenciar o acervo e os empréstimos de uma biblioteca "
                                + "escolar ou comunitária de pequeno porte."));
    }
}
