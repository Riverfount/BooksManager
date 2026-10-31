package com.riverfount.booksmanager.security.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.shared.adapter.in.web.AdminOnly;
import com.riverfount.booksmanager.shared.adapter.in.web.AdminOrOperator;
import com.riverfount.booksmanager.shared.adapter.in.web.AnyProfile;
import com.riverfount.booksmanager.shared.adapter.in.web.CurrentReader;
import com.riverfount.booksmanager.shared.adapter.in.web.PublicEndpoint;
import com.riverfount.booksmanager.shared.adapter.in.web.ReaderOnly;
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
import org.springframework.web.bind.annotation.RequestMapping;
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

        @ReaderOnly
        @RestController
        @RequestMapping("/api/v1/me/_probe")
        static class ReaderProbe {
            @GetMapping
            String reader(@CurrentReader Long readerId) {
                return "reader " + readerId;
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

    // the routes of the reader (Phase 6): /api/v1/me/**, for the READER profile and nobody else

    private String reader(Long readerId) {
        return TestTokens.forReader(encoder, properties, 5L, readerId);
    }

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    private org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping handlerMapping;

    @Test
    void aReaderMayCallTheReaderRoutesAndIsToldWhoTheyAreByTheToken() throws Exception {
        call("/api/v1/me/_probe", reader(42L)).andExpect(status().isOk()).andExpect(content().string("reader 42"));
    }

    @Test
    void aReaderTokenWithoutTheReaderIsRefusedOnTheReaderRoutes() throws Exception {
        assertForbiddenProblem(call("/api/v1/me/_probe", reader(null)));
    }

    @Test
    void theSystemUsersAndUnknownProfilesMayNotCallTheReaderRoutes() throws Exception {
        for (var role : new String[] {"ADMIN", "OPERATOR", null, "", "SUPERUSER", "reader", "ROLE_READER"}) {
            assertForbiddenProblem(call("/api/v1/me/_probe", token(role)));
        }
    }

    @Test
    void withoutATokenTheReaderRoutesAre401() throws Exception {
        call("/api/v1/me/_probe", null).andExpect(status().isUnauthorized());
    }

    @Test
    void theRouteOfTheLoggedUserIsOpenToEveryProfileButNotToUnknownOnes() throws Exception {
        // the probe user (id 7) does not exist: 401 from the controller proves the chain let the profile in
        for (var role : new String[] {"ADMIN", "OPERATOR", "READER"}) {
            call("/api/v1/users/me", token(role)).andExpect(status().isUnauthorized());
        }
        assertForbiddenProblem(call("/api/v1/users/me", token("SUPERUSER")));
    }

    @Test
    void aReaderGets403OnEveryRouteThatIsNotTheirOwnWhateverTheRequest() throws Exception {
        var readerToken = reader(42L);
        var checked = 0;
        for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
            var handler = entry.getValue();
            var type = handler.getBeanType();
            var isOurs = type.getName().startsWith("com.riverfount.booksmanager");
            var isPublic = type.isAnnotationPresent(PublicEndpoint.class)
                    || handler.hasMethodAnnotation(PublicEndpoint.class);
            var isForEveryProfile = type.isAnnotationPresent(AnyProfile.class)
                    || handler.hasMethodAnnotation(AnyProfile.class);
            if (!isOurs || isPublic || isForEveryProfile) {
                continue;
            }
            for (var pattern : entry.getKey().getPathPatternsCondition().getPatternValues()) {
                if (pattern.startsWith("/api/v1/me")) {
                    continue;
                }
                var methods = entry.getKey().getMethodsCondition().getMethods();
                var verbs = methods.isEmpty() ? java.util.Set.of(org.springframework.web.bind.annotation.RequestMethod.GET)
                        : methods;
                for (var verb : verbs) {
                    // the body and the id do not matter: the chain refuses the reader before the controller is reached
                    mockMvc.perform(request(org.springframework.http.HttpMethod.valueOf(verb.name()),
                                    pattern.replaceAll("\\{[^}]+}", "1"))
                                    .header("Authorization", "Bearer " + readerToken)
                                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                            .andExpect(status().isForbidden());
                    checked++;
                }
            }
        }
        // a vacuous pass (no route found) would prove nothing
        org.assertj.core.api.Assertions.assertThat(checked).isGreaterThan(50);
    }
}
