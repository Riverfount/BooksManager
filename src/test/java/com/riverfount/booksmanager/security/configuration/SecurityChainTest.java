package com.riverfount.booksmanager.security.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The real security chain (RF08, RNF04, RNF08): stateless, JWT bearer, CSRF
 * off, 401 as ProblemDetail, and only the documented routes public.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class SecurityChainTest {

    private static final String PROTECTED_ROUTE = "/api/v1/books";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private ResultActions getProtected(String authorization) throws Exception {
        return mockMvc.perform(get(PROTECTED_ROUTE).header("Authorization", authorization));
    }

    private void assertUnauthorizedProblem(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("É necessário estar autenticado com um token válido."))
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    // protected routes need a valid token

    @Test
    void aProtectedRouteWithoutTokenShouldReturn401AsProblemDetail() throws Exception {
        assertUnauthorizedProblem(mockMvc.perform(get(PROTECTED_ROUTE)));
    }

    @Test
    void aValidTokenShouldPassForBothProfiles() throws Exception {
        getProtected(bearer(TestTokens.valid(encoder, properties, "ADMIN"))).andExpect(status().isOk());
        getProtected(bearer(TestTokens.valid(encoder, properties, "OPERATOR"))).andExpect(status().isOk());
    }

    @Test
    void anExpiredTokenShouldReturn401() throws Exception {
        assertUnauthorizedProblem(
                getProtected(bearer(TestTokens.expired(encoder, properties, Duration.ofSeconds(5)))));
    }

    @Test
    void aTamperedTokenShouldReturn401() throws Exception {
        var tampered = TestTokens.tampered(TestTokens.valid(encoder, properties, "OPERATOR"));

        assertUnauthorizedProblem(getProtected(bearer(tampered)));
    }

    @Test
    void aTokenSignedWithAnotherKeyShouldReturn401() throws Exception {
        assertUnauthorizedProblem(getProtected(bearer(TestTokens.signedWithAnotherKey(properties))));
    }

    @Test
    void anUnsignedTokenShouldReturn401() throws Exception {
        assertUnauthorizedProblem(getProtected(bearer(TestTokens.unsigned(properties))));
    }

    @Test
    void aTokenOfAnotherIssuerShouldReturn401() throws Exception {
        assertUnauthorizedProblem(
                getProtected(bearer(TestTokens.fromIssuer(encoder, "someone-else", "ADMIN"))));
    }

    @Test
    void garbageAfterBearerShouldReturn401() throws Exception {
        assertUnauthorizedProblem(getProtected("Bearer not-a-token"));
    }

    @Test
    void theOldBasicLoginShouldNoLongerWork() throws Exception {
        // user:password, base64
        assertUnauthorizedProblem(getProtected("Basic dXNlcjpwYXNzd29yZA=="));
    }

    @Test
    void aTokenInTheQueryStringShouldBeIgnored() throws Exception {
        var token = TestTokens.valid(encoder, properties, "ADMIN");

        assertUnauthorizedProblem(mockMvc.perform(get(PROTECTED_ROUTE).param("access_token", token)));
    }

    // public routes

    @Test
    void theHealthCheckShouldBePublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void theOpenApiDocumentationAndTheSwaggerUiShouldBePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    @Test
    void theAuthRoutesShouldBePublicAndNeedNoCsrfToken() throws Exception {
        // an empty body is refused by validation (400), which proves the request got past the security chain
        // without a token and without a CSRF token
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        // a route that does not exist is a 404 there, not a 401 or a 403
        mockMvc.perform(post("/api/v1/auth/nothing-here").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void otherActuatorEndpointsShouldNotBeReachable() throws Exception {
        var token = bearer(TestTokens.valid(encoder, properties, "ADMIN"));

        mockMvc.perform(get("/actuator/env").header("Authorization", token)).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    // stateless and CSRF

    @Test
    void noSessionShouldBeCreatedOnTheServer() throws Exception {
        var result = getProtected(bearer(TestTokens.valid(encoder, properties, "ADMIN"))).andExpect(status().isOk())
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
    }

    @Test
    void aWriteWithATokenShouldNeedNoCsrfToken() throws Exception {
        // an empty body is refused by validation (400), which proves it got past the security chain
        mockMvc.perform(post("/api/v1/authors")
                        .header("Authorization", bearer(TestTokens.valid(encoder, properties, "OPERATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aWriteWithoutATokenShouldBeRefusedWith401NotWith403() throws Exception {
        assertUnauthorizedProblem(mockMvc.perform(post("/api/v1/authors")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Machado de Assis\"}")));
    }
}
