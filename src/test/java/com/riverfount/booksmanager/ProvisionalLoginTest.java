package com.riverfount.booksmanager;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Guards the provisional login of the application (phase 0) while the
 * authentication phase does not exist: Spring Boot's default user, named
 * "user", with the password generated at each run and printed in the
 * console, is how the developer reaches the API and the Swagger UI.
 *
 * <p>It broke once because a {@code PasswordEncoder} bean in the context makes
 * Spring Boot stop prefixing the generated password with {noop}, so it is
 * compared as a hash and no password works. Delete this test when the JWT
 * security chain (issue #101) replaces the default user.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ProvisionalLoginTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityProperties securityProperties;

    @Test
    void theGeneratedPasswordOfTheDefaultUserShouldLogIn() throws Exception {
        var user = securityProperties.getUser();

        mockMvc.perform(get("/api/v1/books").with(httpBasic(user.getName(), user.getPassword())))
                .andExpect(status().isOk());
    }

    @Test
    void aWrongPasswordShouldBeRefused() throws Exception {
        mockMvc.perform(get("/api/v1/books").with(httpBasic("user", "not-the-generated-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withoutCredentialsTheApiShouldBeRefused() throws Exception {
        mockMvc.perform(get("/api/v1/books")).andExpect(status().isUnauthorized());
    }
}
