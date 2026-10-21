package com.riverfount.booksmanager.security.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.shared.adapter.in.web.AdminOnly;
import com.riverfount.booksmanager.shared.adapter.in.web.AdminOrOperator;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authorization by profile (RNF08) with the real chain and real tokens: the
 * routes of the catalog and of the readers are for an administrator or an
 * operator, a route marked for administrators only refuses an operator with a
 * 403 in ProblemDetail, and a token with no known profile gets in nowhere.
 *
 * <p>No route is for administrators only yet (the system users and the library
 * configuration come later), so the probe controller below stands for them.
 */
@Import({TestcontainersConfiguration.class, SecurityAuthorizationTest.ProbeConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class SecurityAuthorizationTest {

    @TestConfiguration
    static class ProbeConfiguration {
        @AdminOnly
        @RestController
        static class AdministratorsOnlyProbe {
            @GetMapping("/api/v1/_probe/admin")
            String admin() {
                return "admin area";
            }
        }

        @AdminOrOperator
        @RestController
        static class ClassDefaultWithAStricterMethodProbe {
            @GetMapping("/api/v1/_probe/shared")
            String shared() {
                return "shared area";
            }

            @AdminOnly
            @GetMapping("/api/v1/_probe/shared-but-admin")
            String sharedButAdmin() {
                return "admin method of a shared controller";
            }
        }
    }

    private static final String[] ROUTES_FOR_ADMIN_AND_OPERATOR = {
            "/api/v1/books", "/api/v1/authors", "/api/v1/categories", "/api/v1/readers", "/api/v1/copies"};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ResultActions call(String route, String token) throws Exception {
        var request = get(route);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private String token(String role) {
        return TestTokens.signed(encoder, properties.issuer(), "7", role, Instant.now(),
                Instant.now().plusSeconds(900));
    }

    private void assertForbiddenProblem(ResultActions result) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("Você não tem permissão para acessar este recurso."));
    }

    // the routes for administrators and operators

    @Test
    void anAdministratorAndAnOperatorMayCallTheCatalogAndReaderRoutes() throws Exception {
        for (var route : ROUTES_FOR_ADMIN_AND_OPERATOR) {
            call(route, token("ADMIN")).andExpect(status().isOk());
            call(route, token("OPERATOR")).andExpect(status().isOk());
        }
    }

    @Test
    void aValidTokenWithoutAKnownProfileMayCallNoRoute() throws Exception {
        // properly signed and not expired, but the profile is missing, unknown or in the wrong case
        for (var role : new String[] {null, "", "SUPERUSER", "admin", "Operator", "ROLE_ADMIN", "USER"}) {
            for (var route : ROUTES_FOR_ADMIN_AND_OPERATOR) {
                assertForbiddenProblem(call(route, token(role)));
            }
        }
    }

    @Test
    void withoutATokenEveryProtectedRouteIs401NotA403() throws Exception {
        for (var route : ROUTES_FOR_ADMIN_AND_OPERATOR) {
            call(route, null).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void theWritingRoutesAreForAdministratorsAndOperatorsToo() throws Exception {
        // an empty body is refused by validation (400), which proves the caller got past the authorization
        for (var role : new String[] {"ADMIN", "OPERATOR"}) {
            mockMvc.perform(post("/api/v1/authors").header("Authorization", "Bearer " + token(role))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void aValidWriteFromAnUnknownProfileIs403AndCreatesNothing() throws Exception {
        for (var role : new String[] {null, "SUPERUSER", "admin"}) {
            assertForbiddenProblem(mockMvc.perform(post("/api/v1/authors")
                    .header("Authorization", "Bearer " + token(role))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Authorization Probe Author\"}")));
        }

        assertThat(jdbcTemplate.queryForObject("select count(*) from author where name = 'Authorization Probe Author'",
                Integer.class)).isZero();
    }

    // a route for administrators only

    @Test
    void anAdministratorMayCallARouteForAdministratorsOnly() throws Exception {
        call("/api/v1/_probe/admin", token("ADMIN")).andExpect(status().isOk());
    }

    @Test
    void anOperatorGets403InProblemDetailOnARouteForAdministratorsOnly() throws Exception {
        assertForbiddenProblem(call("/api/v1/_probe/admin", token("OPERATOR")));
    }

    @Test
    void aTokenWithoutAKnownProfileGets403OnARouteForAdministratorsOnly() throws Exception {
        for (var role : new String[] {null, "SUPERUSER", "admin"}) {
            assertForbiddenProblem(call("/api/v1/_probe/admin", token(role)));
        }
    }

    @Test
    void withoutATokenARouteForAdministratorsOnlyIs401() throws Exception {
        call("/api/v1/_probe/admin", null).andExpect(status().isUnauthorized());
    }

    @Test
    void aMethodMarkedForAdministratorsOverridesTheDefaultOfItsController() throws Exception {
        call("/api/v1/_probe/shared", token("OPERATOR")).andExpect(status().isOk());
        call("/api/v1/_probe/shared-but-admin", token("ADMIN")).andExpect(status().isOk());
        assertForbiddenProblem(call("/api/v1/_probe/shared-but-admin", token("OPERATOR")));
    }

    // what a refusal says

    @Test
    void theRefusalShouldNotTellWhichProfileWouldHaveBeenAllowed() throws Exception {
        var body = call("/api/v1/_probe/admin", token("OPERATOR")).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("ADMIN").doesNotContain("hasRole").doesNotContain("OPERATOR");
    }

    @Test
    void theRefusalShouldNotAskTheClientToAuthenticateAgain() throws Exception {
        var response = call("/api/v1/_probe/admin", token("OPERATOR")).andReturn().getResponse();

        assertThat(response.getHeader("WWW-Authenticate")).isNull();
    }

    // the public routes keep needing no profile

    @Test
    void thePublicRoutesNeedNoTokenAndNoProfile() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isForbidden()); // the CSRF header, not the profile
        mockMvc.perform(post("/api/v1/auth/logout").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
