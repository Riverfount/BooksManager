package com.riverfount.booksmanager.shared.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * General API information (title, version, description) shown in the
 * Swagger UI, instead of springdoc's defaults (RNF24). The version shown is
 * the version of the project, read from the {@code pom.xml}; it is not the
 * version of the API, which is the {@code /api/v1} prefix of the routes and
 * does not change with it. Also the Bearer (JWT)
 * security scheme that every route uses except the public ones (RF08). Every
 * operation that needs a token also documents the 401 and the 403 (RNF08).
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";

    /** Shown when the application runs without the build information (for example, straight from an IDE). */
    static final String UNKNOWN_VERSION = "dev";

    @Bean
    OpenAPI booksManagerOpenApi(ObjectProvider<BuildProperties> buildProperties) {
        var shownVersion = buildProperties.stream().findFirst().map(properties -> "v" + properties.getVersion())
                .orElse(UNKNOWN_VERSION);
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
                        .version(shownVersion)
                        .description("API REST para gerenciar o acervo e os empréstimos de uma biblioteca "
                                + "escolar ou comunitária de pequeno porte."));
    }

    /**
     * Adds the 401 (no valid token) and the 403 (the profile may not call it),
     * both as ProblemDetail, to every operation that needs a token, so that a
     * client generated from the contract knows these errors exist. The public
     * operations (the ones that declare an empty security requirement) are left
     * alone, and a response an operation already documents is never replaced.
     */
    @Bean
    OpenApiCustomizer authorizationResponsesCustomizer() {
        return openApi -> openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
            if (operation.getSecurity() != null && operation.getSecurity().isEmpty()) {
                return;
            }
            addIfAbsent(operation, "401", "Não autenticado: token ausente, expirado ou inválido");
            addIfAbsent(operation, "403", "Sem permissão: o perfil do token não pode chamar esta rota");
        }));
    }

    /**
     * Describes the {@code errors} property that the 400 of an invalid request adds to the ProblemDetail (RNF04): the
     * list of the fields that failed, each with its path and a message in Portuguese. The property is added by the
     * exception handler at run time, so springdoc, which reads the class, does not know it.
     */
    @Bean
    OpenApiCustomizer invalidFieldsCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            var problem = openApi.getComponents().getSchemas().get("ProblemDetail");
            if (problem == null) {
                return;
            }
            var item = new ObjectSchema()
                    .description("Um campo inválido da requisição")
                    .addProperty("field", new StringSchema()
                            .description("Caminho do campo na requisição, como o cliente o enviou; ausente quando o "
                                    + "erro é sobre o objeto inteiro")
                            .example("guardian.email"))
                    .addProperty("message", new StringSchema()
                            .description("O que está errado, em português").example("o e-mail é obrigatório"))
                    .addRequiredItem("message");
            problem.addProperty("errors", new ArraySchema().items(item)
                    .description("Só nos 400 de requisição inválida: os campos que falharam, em ordem de campo"));
        };
    }

    private static void addIfAbsent(Operation operation, String status, String description) {
        if (operation.getResponses() == null || operation.getResponses().containsKey(status)) {
            return;
        }
        var problem = new Schema<>().$ref("#/components/schemas/ProblemDetail");
        operation.getResponses().addApiResponse(status, new ApiResponse().description(description)
                .content(new Content().addMediaType("application/problem+json", new MediaType().schema(problem))));
    }
}
