package com.riverfount.booksmanager.user.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.WithAdmin;
import com.riverfount.booksmanager.WithOperator;
import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.user.application.port.in.InviteReaderAccessCommand;
import com.riverfount.booksmanager.user.application.port.in.InviteReaderAccessUseCase;
import com.riverfount.booksmanager.user.application.port.in.ReaderAccessInvitation;
import com.riverfount.booksmanager.user.application.port.in.RevokeReaderAccessUseCase;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The routes that give and take away the access of a reader (Phase 6): for the administrator and the operator. */
@WebMvcTest(ReaderAccessController.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class})
class ReaderAccessControllerTest {

    private static final Instant EXPIRES = Instant.parse("2026-10-29T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InviteReaderAccessUseCase inviteUseCase;

    @MockitoBean
    private RevokeReaderAccessUseCase revokeUseCase;

    @Test
    @WithOperator
    void shouldInviteTheReaderAndAnswerWhenTheLinkExpiresWithoutTheToken() throws Exception {
        given(inviteUseCase.invite(any())).willReturn(new ReaderAccessInvitation(7L, EXPIRES));

        mockMvc.perform(post("/api/v1/readers/7/access-invitations").with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.readerId").value(7))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-29T12:00:00Z"))
                .andExpect(jsonPath("$.token").doesNotExist());

        then(inviteUseCase).should().invite(new InviteReaderAccessCommand(7L));
    }

    @Test
    @WithAdmin
    void anAdministratorMayInviteToo() throws Exception {
        given(inviteUseCase.invite(any())).willReturn(new ReaderAccessInvitation(7L, EXPIRES));

        mockMvc.perform(post("/api/v1/readers/7/access-invitations").with(csrf())).andExpect(status().isAccepted());
    }

    @Test
    @WithOperator
    void shouldAnswer404ForAnUnknownReader() throws Exception {
        given(inviteUseCase.invite(any())).willThrow(new ResourceNotFoundException("leitor não encontrado: 9"));

        mockMvc.perform(post("/api/v1/readers/9/access-invitations").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    @WithOperator
    void shouldAnswer422WhenTheReaderCannotBeInvited() throws Exception {
        given(inviteUseCase.invite(any())).willThrow(new BusinessRuleException("o leitor já tem acesso ao sistema"));

        mockMvc.perform(post("/api/v1/readers/7/access-invitations").with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("o leitor já tem acesso ao sistema"));
    }

    @Test
    @WithOperator
    void shouldRevokeTheAccess() throws Exception {
        mockMvc.perform(delete("/api/v1/readers/7/access").with(csrf())).andExpect(status().isNoContent());

        then(revokeUseCase).should().revoke(7L);
    }

    @Test
    @WithOperator
    void shouldAnswer404WhenRevokingTheAccessOfAnUnknownReader() throws Exception {
        willThrow(new ResourceNotFoundException("leitor não encontrado: 9")).given(revokeUseCase).revoke(9L);

        mockMvc.perform(delete("/api/v1/readers/9/access").with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "READER")
    void aReaderMayNotGiveOrTakeAwayAccess() throws Exception {
        mockMvc.perform(post("/api/v1/readers/7/access-invitations").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/readers/7/access").with(csrf())).andExpect(status().isForbidden());

        org.mockito.Mockito.verifyNoInteractions(inviteUseCase, revokeUseCase);
    }

    @Test
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/readers/7/access-invitations").with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/readers/7/access").with(csrf())).andExpect(status().isUnauthorized());
    }
}
