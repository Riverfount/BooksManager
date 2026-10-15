package com.riverfount.booksmanager.shared.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * General API information (title, version, description) shown in the
 * Swagger UI, instead of springdoc's defaults (RNF24), and the Bearer (JWT)
 * security scheme that every route uses except the public ones (RF08).
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI booksManagerOpenApi() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token JWT, obtido no login. Envie no cabeçalho "
                                + "`Authorization: Bearer <token>`.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .info(new Info()
                        .title("Books Manager API")
                        .version("v1")
                        .description("API REST para gerenciar o acervo e os empréstimos de uma biblioteca "
                                + "escolar ou comunitária de pequeno porte."));
    }
}
