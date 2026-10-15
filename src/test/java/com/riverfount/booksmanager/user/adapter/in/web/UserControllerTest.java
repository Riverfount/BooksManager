package com.riverfount.booksmanager.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.WithAdmin;
import com.riverfount.booksmanager.WithOperator;
import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.user.application.port.in.CreateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.CreateUserUseCase;
import com.riverfount.booksmanager.user.domain.Role;
import com.riverfount.booksmanager.user.domain.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(UserController.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class})
class UserControllerTest {

    private static final String BODY = "{\"login\":\"maria\",\"password\":\"N3w-Pass#word\",\"role\":\"OPERATOR\"}";
    private static final String HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BzqhZ8H9V5xQe4Fq0sDq6Z0yR3Wm";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateUserUseCase createUserUseCase;

    private MockHttpServletRequestBuilder create(String json) {
        return post("/api/v1/users").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private void givenTheUserIsCreated() {
        given(createUserUseCase.create(any())).willReturn(User.reconstitute(5L, "maria", HASH, Role.OPERATOR, true));
    }

    @Test
    @WithAdmin
    void shouldCreateAndReturn201WithLocationAndTheUserWithoutAnySecret() throws Exception {
        givenTheUserIsCreated();

        var response = mockMvc.perform(create(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/users/5")))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.login").value("maria"))
                .andExpect(jsonPath("$.role").value("OPERATOR"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse();

        assertThat(response.getContentAsString()).doesNotContain("password").doesNotContain("Password")
                .doesNotContain("hash").doesNotContain(HASH).doesNotContain("N3w-Pass#word");
    }

    @Test
    @WithAdmin
    void shouldHandTheRequestToTheUseCaseAsACommand() throws Exception {
        givenTheUserIsCreated();

        mockMvc.perform(create(BODY)).andExpect(status().isCreated());

        var captor = ArgumentCaptor.forClass(CreateUserCommand.class);
        then(createUserUseCase).should().create(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new CreateUserCommand("maria", "N3w-Pass#word", Role.OPERATOR));
    }

    @Test
    @WithAdmin
    void shouldAcceptTheAdministratorProfile() throws Exception {
        given(createUserUseCase.create(any())).willReturn(User.reconstitute(6L, "chefe", HASH, Role.ADMIN, true));

        mockMvc.perform(create("{\"login\":\"chefe\",\"password\":\"N3w-Pass#word\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @WithAdmin
    void shouldReturn400AndNeverCallTheUseCaseForAMissingOrBlankField() throws Exception {
        for (var json : new String[] {"{}", "{\"password\":\"N3w-Pass#word\",\"role\":\"OPERATOR\"}",
                "{\"login\":\"maria\",\"role\":\"OPERATOR\"}", "{\"login\":\"maria\",\"password\":\"N3w-Pass#word\"}",
                "{\"login\":\"  \",\"password\":\"N3w-Pass#word\",\"role\":\"OPERATOR\"}",
                "{\"login\":\"maria\",\"password\":\"  \",\"role\":\"OPERATOR\"}"}) {
            mockMvc.perform(create(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        }

        then(createUserUseCase).should(never()).create(any());
    }

    @Test
    @WithAdmin
    void shouldReturn400ForAnUnknownProfileOrAMalformedBody() throws Exception {
        mockMvc.perform(create("{\"login\":\"maria\",\"password\":\"N3w-Pass#word\",\"role\":\"SUPERUSER\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(create("{\"login\":\"maria\",\"password\":\"N3w-Pass#word\",\"role\":\"operator\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(create("not json")).andExpect(status().isBadRequest());

        then(createUserUseCase).should(never()).create(any());
    }

    @Test
    @WithAdmin
    void shouldReturn400ForAnOversizedLoginOrPassword() throws Exception {
        mockMvc.perform(create("{\"login\":\"" + "a".repeat(51)
                + "\",\"password\":\"N3w-Pass#word\",\"role\":\"OPERATOR\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(create("{\"login\":\"maria\",\"password\":\"" + "a".repeat(129)
                + "\",\"role\":\"OPERATOR\"}")).andExpect(status().isBadRequest());

        then(createUserUseCase).should(never()).create(any());
    }

    @Test
    @WithAdmin
    void shouldReturn422WithProblemDetailForARepeatedLogin() throws Exception {
        given(createUserUseCase.create(any()))
                .willThrow(new BusinessRuleException("já existe um usuário com o login 'maria'"));

        mockMvc.perform(create(BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("já existe um usuário com o login 'maria'"));
    }

    @Test
    @WithAdmin
    void shouldReturn422NamingTheMissingRulesOfAWeakPassword() throws Exception {
        given(createUserUseCase.create(any())).willThrow(
                new BusinessRuleException("a senha não atende às regras: deve ter ao menos um número"));

        mockMvc.perform(create(BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("a senha não atende às regras")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("N3w-Pass#word"))));
    }

    // only an administrator (RNF08)

    @Test
    @WithOperator
    void anOperatorGets403AndNothingIsCreated() throws Exception {
        mockMvc.perform(create(BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("Você não tem permissão para acessar este recurso."));

        then(createUserUseCase).should(never()).create(any());
    }

    @Test
    @WithMockUser(roles = "USER")
    void aUserWithAnotherProfileGets403() throws Exception {
        mockMvc.perform(create(BODY)).andExpect(status().isForbidden());

        then(createUserUseCase).should(never()).create(any());
    }

    @Test
    @WithMockUser(roles = {})
    void aUserWithNoProfileGets403() throws Exception {
        mockMvc.perform(create(BODY)).andExpect(status().isForbidden());

        then(createUserUseCase).should(never()).create(any());
    }
}
