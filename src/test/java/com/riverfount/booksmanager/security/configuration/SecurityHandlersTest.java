package com.riverfount.booksmanager.security.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * The 401 and the 403 as ProblemDetail (RNF04), both when they come out of
 * the security filters (entry point and access denied handler) and when an
 * authorization failure is thrown from inside a controller. The 403 has no
 * route to come from in the real chain until the profile rules arrive
 * (issue #105), so it is exercised directly and through a probe controller.
 */
@Import({TestcontainersConfiguration.class, SecurityHandlersTest.ProbeConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
class SecurityHandlersTest {

    @TestConfiguration
    static class ProbeConfiguration {
        @RestController
        static class ProbeController {
            @GetMapping("/api/v1/_probe/forbidden")
            String forbidden() {
                throw new AccessDeniedException("not allowed: internal detail");
            }

            @GetMapping("/api/v1/_probe/unauthenticated")
            String unauthenticated() {
                throw new BadCredentialsException("bad: internal detail");
            }
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    @Autowired
    @Qualifier("handlerExceptionResolver")
    private HandlerExceptionResolver resolver;

    @Test
    void theAccessDeniedHandlerShouldAnswer403AsProblemDetail() throws Exception {
        var handler = new ProblemDetailAccessDeniedHandler(resolver);
        var request = new MockHttpServletRequest("GET", "/api/v1/users");
        var response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("internal detail"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString())
                .contains("\"status\":403")
                .contains("Você não tem permissão para acessar este recurso.")
                .doesNotContain("internal detail");
    }

    @Test
    void theEntryPointShouldAnswer401AsProblemDetailWithTheBearerChallenge() throws Exception {
        var entryPoint = new ProblemDetailAuthenticationEntryPoint(resolver);
        var request = new MockHttpServletRequest("GET", "/api/v1/books");
        var response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("internal detail"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getContentAsString())
                .contains("\"status\":401")
                .doesNotContain("internal detail");
    }

    @Test
    void anAccessDeniedFailureInsideAControllerShouldBeA403ProblemDetail() throws Exception {
        var token = "Bearer " + TestTokens.valid(encoder, properties, "OPERATOR");

        mockMvc.perform(get("/api/v1/_probe/forbidden").header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Você não tem permissão para acessar este recurso."));
    }

    @Test
    void anAuthenticationFailureInsideAControllerShouldBeA401ProblemDetail() throws Exception {
        var token = "Bearer " + TestTokens.valid(encoder, properties, "OPERATOR");

        mockMvc.perform(get("/api/v1/_probe/unauthenticated").header("Authorization", token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401));
    }
}
