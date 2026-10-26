package com.riverfount.booksmanager.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.user.application.port.in.AcceptReaderInvitationCommand;
import com.riverfount.booksmanager.user.application.port.in.AcceptReaderInvitationUseCase;
import com.riverfount.booksmanager.user.application.port.in.RequestReaderPasswordResetCommand;
import com.riverfount.booksmanager.user.application.port.in.RequestReaderPasswordResetUseCase;
import com.riverfount.booksmanager.user.application.port.in.ResetReaderPasswordCommand;
import com.riverfount.booksmanager.user.application.port.in.ResetReaderPasswordUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The public routes where a reader accepts an invitation and recovers the password (Phase 6). */
@WebMvcTest(ReaderAccessAuthController.class)
@Import(GlobalExceptionHandler.class)
class ReaderAccessAuthControllerTest {

    private static final String ACCEPT = "{\"token\":\"the-token\",\"login\":\"ana\",\"password\":\"N3w-Pass#word\"}";
    private static final String RESET = "{\"token\":\"the-token\",\"password\":\"N3w-Pass#word\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AcceptReaderInvitationUseCase acceptUseCase;

    @MockitoBean
    private RequestReaderPasswordResetUseCase requestResetUseCase;

    @MockitoBean
    private ResetReaderPasswordUseCase resetUseCase;

    private static MockHttpServletRequestBuilder json(String path, String body) {
        return post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    @WithMockUser
    void shouldAcceptTheInvitation() throws Exception {
        mockMvc.perform(json("/api/v1/auth/accept-invitation", ACCEPT)).andExpect(status().isNoContent());

        then(acceptUseCase).should().accept(new AcceptReaderInvitationCommand("the-token", "ana", "N3w-Pass#word"));
    }

    @Test
    @WithMockUser
    void shouldAnswer422ForAnInvalidInvitationAndNeverEchoTheSecrets() throws Exception {
        willThrow(new BusinessRuleException("convite inválido ou expirado")).given(acceptUseCase).accept(
                new AcceptReaderInvitationCommand("the-token", "ana", "N3w-Pass#word"));

        var body = mockMvc.perform(json("/api/v1/auth/accept-invitation", ACCEPT))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("convite inválido ou expirado"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("the-token").doesNotContain("N3w-Pass#word");
    }

    @Test
    @WithMockUser
    void shouldRefuseAnInvitationWithMissingFields() throws Exception {
        for (var body : new String[] {"{}", "{\"login\":\"ana\",\"password\":\"N3w-Pass#word\"}",
                "{\"token\":\"t\",\"password\":\"N3w-Pass#word\"}", "{\"token\":\"t\",\"login\":\"ana\"}",
                "{\"token\":\" \",\"login\":\"ana\",\"password\":\"N3w-Pass#word\"}"}) {
            mockMvc.perform(json("/api/v1/auth/accept-invitation", body)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").isNotEmpty());
        }
        then(acceptUseCase).should(never()).accept(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @WithMockUser
    void shouldAnswer204ToAForgotPasswordRequest() throws Exception {
        mockMvc.perform(json("/api/v1/auth/forgot-password", "{\"email\":\"ana@example.com\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        then(requestResetUseCase).should().requestReset(new RequestReaderPasswordResetCommand("ana@example.com"));
    }

    @Test
    @WithMockUser
    void shouldRefuseAForgotPasswordRequestWithoutAnEmail() throws Exception {
        mockMvc.perform(json("/api/v1/auth/forgot-password", "{}")).andExpect(status().isBadRequest());
        mockMvc.perform(json("/api/v1/auth/forgot-password", "{\"email\":\" \"}")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void shouldResetThePassword() throws Exception {
        mockMvc.perform(json("/api/v1/auth/reset-password", RESET)).andExpect(status().isNoContent());

        then(resetUseCase).should().reset(new ResetReaderPasswordCommand("the-token", "N3w-Pass#word"));
    }

    @Test
    @WithMockUser
    void shouldAnswer422ForAnInvalidResetLink() throws Exception {
        willThrow(new BusinessRuleException("link de redefinição de senha inválido ou expirado")).given(resetUseCase)
                .reset(new ResetReaderPasswordCommand("the-token", "N3w-Pass#word"));

        mockMvc.perform(json("/api/v1/auth/reset-password", RESET)).andExpect(status().isUnprocessableContent());
    }

    @Test
    @WithMockUser
    void shouldRefuseAResetWithMissingFields() throws Exception {
        mockMvc.perform(json("/api/v1/auth/reset-password", "{}")).andExpect(status().isBadRequest());
        mockMvc.perform(json("/api/v1/auth/reset-password", "{\"token\":\"t\"}")).andExpect(status().isBadRequest());
    }
}
