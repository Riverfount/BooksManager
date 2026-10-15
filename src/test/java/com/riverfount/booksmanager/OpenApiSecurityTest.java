package com.riverfount.booksmanager;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The OpenAPI contract (RNF24) tells the clients how to authenticate: a
 * Bearer JWT scheme, required by default on every operation (RF08).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldDeclareTheBearerJwtScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"));
    }

    @Test
    void shouldRequireTheSchemeByDefault() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray());
    }

    @Test
    void theLoginOperationShouldNeedNoToken() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty());
    }

    @Test
    void theLoginOperationShouldDescribeTheCookieAndTheErrors() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['401']").exists());
    }

    @Test
    void theRefreshAndLogoutOperationsShouldNeedNoBearerTokenAndDocumentTheCsrfHeader() throws Exception {
        for (var operation : new String[] {"refresh", "logout"}) {
            var path = "$.paths['/api/v1/auth/" + operation + "'].post";
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(jsonPath(path + ".security").isEmpty())
                    .andExpect(jsonPath(path + ".parameters[?(@.name == 'X-Requested-With')].required")
                            .value(org.hamcrest.Matchers.hasItem(true)))
                    .andExpect(jsonPath(path + ".responses['403']").exists());
        }
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.responses['204']").exists());
    }

    @Test
    void everyProtectedOperationShouldDocumentThe401AndThe403AsProblemDetail() throws Exception {
        for (var path : new String[] {"/api/v1/books", "/api/v1/authors", "/api/v1/categories", "/api/v1/readers",
                "/api/v1/copies"}) {
            for (var status : new String[] {"401", "403"}) {
                mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(jsonPath("$.paths['" + path + "'].get.responses['" + status + "']").exists())
                        .andExpect(jsonPath("$.paths['" + path + "'].get.responses['" + status
                                + "'].content['application/problem+json'].schema.$ref")
                                .value("#/components/schemas/ProblemDetail"));
            }
        }
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/readers/{id}'].delete.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/readers/{id}/block'].post.responses['401']").exists());
    }

    @Test
    void thePublicOperationsShouldNotGetTheGenericAuthorizationResponses() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['403']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.responses['401']").doesNotExist());
    }

    @Test
    void anExistingResponseShouldNeverBeOverwrittenByTheGenericOne() throws Exception {
        // the login documents its own 401 (wrong credentials), and refresh its own 401 and 403
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['401'].description")
                        .value("Login ou senha inválidos"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.responses['403'].description")
                        .value(org.hamcrest.Matchers.containsString("X-Requested-With")));
    }
}
