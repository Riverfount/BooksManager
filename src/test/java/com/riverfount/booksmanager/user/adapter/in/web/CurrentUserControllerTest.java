package com.riverfount.booksmanager.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.user.application.port.in.GetUserUseCase;
import com.riverfount.booksmanager.user.domain.Role;
import com.riverfount.booksmanager.user.domain.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Who is logged in (the cartão of the sidebar): any profile reads its own data, nobody else's. */
@WebMvcTest(CurrentUserController.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class})
class CurrentUserControllerTest {

    private static final String URL = "/api/v1/users/me";
    private static final String HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BzqhZ8H9V5xQe4Fq0sDq6Z0yR3Wm";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetUserUseCase getUserUseCase;

    private static RequestPostProcessor caller(String subject, String role) {
        return jwt().jwt(token -> token.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void anOperatorShouldReadItsOwnData() throws Exception {
        given(getUserUseCase.findById(7L)).willReturn(Optional.of(
                User.reconstitute(7L, "carlos", HASH, Role.OPERATOR, true, 0, null, "Carlos Mendes")));

        var body = mockMvc.perform(get(URL).with(caller("7", "OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.login").value("carlos"))
                .andExpect(jsonPath("$.displayName").value("Carlos Mendes"))
                .andExpect(jsonPath("$.role").value("OPERATOR"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(HASH).doesNotContain("assword").doesNotContain("hash");
    }

    @Test
    void anAdministratorShouldReadItsOwnDataAndNotTheOneInTheUrl() throws Exception {
        given(getUserUseCase.findById(1L)).willReturn(Optional.of(
                User.reconstitute(1L, "admin", HASH, Role.ADMIN, true)));

        mockMvc.perform(get(URL).with(caller("1", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.displayName").doesNotExist());
    }

    @Test
    void aReaderShouldReadItsOwnData() throws Exception {
        given(getUserUseCase.findById(9L)).willReturn(Optional.of(
                User.reconstitute(9L, "ana", HASH, Role.READER, true)));

        mockMvc.perform(get(URL).with(caller("9", "READER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.login").value("ana"))
                .andExpect(jsonPath("$.role").value("READER"));
    }

    @Test
    void anInactiveReaderShouldAnswer401() throws Exception {
        given(getUserUseCase.findById(9L)).willReturn(Optional.of(
                User.reconstitute(9L, "ana", HASH, Role.READER, false)));

        mockMvc.perform(get(URL).with(caller("9", "READER"))).andExpect(status().isUnauthorized());
    }

    @Test
    void withoutAuthenticationShouldAnswer401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenWithoutAProfileShouldAnswer403() throws Exception {
        mockMvc.perform(get(URL).with(jwt().jwt(token -> token.subject("7")))).andExpect(status().isForbidden());
    }

    @Test
    void anInactiveUserShouldAnswer401() throws Exception {
        given(getUserUseCase.findById(7L)).willReturn(Optional.of(
                User.reconstitute(7L, "carlos", HASH, Role.OPERATOR, false)));

        mockMvc.perform(get(URL).with(caller("7", "OPERATOR")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    void aUserThatNoLongerExistsShouldAnswer401() throws Exception {
        given(getUserUseCase.findById(7L)).willReturn(Optional.empty());

        mockMvc.perform(get(URL).with(caller("7", "OPERATOR"))).andExpect(status().isUnauthorized());
    }

    @Test
    void aSubjectThatIsNotAnIdShouldBeRefused() throws Exception {
        mockMvc.perform(get(URL).with(caller("abc", "OPERATOR"))).andExpect(status().isForbidden());
    }
}
