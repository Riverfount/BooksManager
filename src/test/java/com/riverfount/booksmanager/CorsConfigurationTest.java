package com.riverfount.booksmanager;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the CORS restriction required by RNF09: only the configured
 * front-end origin gets CORS headers, including on the preflight request,
 * which carries no credentials and must not be stopped by the login.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class CorsConfigurationTest {

    /** An authenticated caller with the profile the catalog routes require (RNF08). */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor operator() {
        return jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERATOR"));
    }

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String OTHER_ORIGIN = "http://evil.example.com";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldAnswerThePreflightOfTheAllowedOriginWithoutAuthentication() throws Exception {
        mockMvc.perform(options("/api/v1/books/1")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("PATCH")))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsString("Authorization")));
    }

    @Test
    void shouldAllowEveryMethodOfTheApi() throws Exception {
        for (var method : new String[] {"GET", "POST", "PATCH", "DELETE"}) {
            mockMvc.perform(options("/api/v1/books")
                            .header("Origin", ALLOWED_ORIGIN)
                            .header("Access-Control-Request-Method", method))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void shouldRejectThePreflightOfAnotherOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/books")
                        .header("Origin", OTHER_ORIGIN)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void shouldRejectAMethodOutsideTheAllowedOnes() throws Exception {
        mockMvc.perform(options("/api/v1/books/1")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "PUT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectAHeaderOutsideTheAllowedOnes() throws Exception {
        mockMvc.perform(options("/api/v1/books")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "X-Custom"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAddCorsHeadersToTheActualRequestOfTheAllowedOrigin() throws Exception {
        mockMvc.perform(get("/api/v1/books").header("Origin", ALLOWED_ORIGIN).with(operator()))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }

    @Test
    void shouldNotAddCorsHeadersToTheActualRequestOfAnotherOrigin() throws Exception {
        mockMvc.perform(get("/api/v1/books").header("Origin", OTHER_ORIGIN).with(operator()))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void shouldAllowCredentialsForTheAllowedOriginOnThePreflightAndOnTheActualRequest() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mockMvc.perform(get("/api/v1/books").header("Origin", ALLOWED_ORIGIN).with(operator()))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void shouldNeverAnswerWithAWildcardOriginSinceCredentialsAreAllowed() throws Exception {
        mockMvc.perform(options("/api/v1/books")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }

    @Test
    void shouldNotAllowCredentialsToAnotherOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", OTHER_ORIGIN)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void shouldAllowTheCsrfHeaderOfTheRefreshAndLogoutRoutes() throws Exception {
        for (var route : new String[] {"/api/v1/auth/refresh", "/api/v1/auth/logout"}) {
            mockMvc.perform(options(route)
                            .header("Origin", ALLOWED_ORIGIN)
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "X-Requested-With"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Headers",
                            org.hamcrest.Matchers.containsString("X-Requested-With")))
                    .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        }
    }
}
