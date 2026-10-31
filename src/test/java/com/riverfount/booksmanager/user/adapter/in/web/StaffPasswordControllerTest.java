package com.riverfount.booksmanager.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.user.application.port.in.ChangeStaffPasswordCommand;
import com.riverfount.booksmanager.user.application.port.in.ChangeStaffPasswordUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The staff changes their own password (RF09): the user is the one of the token and never comes from the path or the
 * body; the answer is a 204 with no body; the rules (current password, lockout, policy, inactive user) are the use
 * case's and reach the client as 422 in ProblemDetail; a reader token is refused.
 */
@WebMvcTest(StaffPasswordController.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class})
class StaffPasswordControllerTest {

    private static final String URL = "/api/v1/users/me/password";
    private static final String BODY = "{\"currentPassword\":\"Cur#rent1Pass\",\"newPassword\":\"N3w-Pass#word\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChangeStaffPasswordUseCase useCase;

    private static RequestPostProcessor caller(String subject, String role) {
        return jwt().jwt(token -> token.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private MockHttpServletRequestBuilder change(String body) {
        return post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void anOperatorAndAnAdministratorChangeTheirOwnPasswordAndGet204WithNoBody() throws Exception {
        for (var role : new String[] {"OPERATOR", "ADMIN"}) {
            mockMvc.perform(change(BODY).with(caller("7", role))).andExpect(status().isNoContent())
                    .andExpect(content().string(""));
        }

        then(useCase).should(org.mockito.Mockito.times(2))
                .change(new ChangeStaffPasswordCommand(7L, "Cur#rent1Pass", "N3w-Pass#word"));
    }

    @Test
    void theUserIsTheOneOfTheTokenAndIgnoresAnyIdInTheBody() throws Exception {
        mockMvc.perform(change("{\"id\":99,\"userId\":99,\"currentPassword\":\"Cur#rent1Pass\","
                + "\"newPassword\":\"N3w-Pass#word\"}").with(caller("7", "OPERATOR"))).andExpect(status().isNoContent());

        then(useCase).should().change(new ChangeStaffPasswordCommand(7L, "Cur#rent1Pass", "N3w-Pass#word"));
    }

    @Test
    void blankPasswordsGet400AndNothingReachesTheUseCase() throws Exception {
        mockMvc.perform(change("{\"currentPassword\":\"\",\"newPassword\":\"N3w-Pass#word\"}")
                        .with(caller("7", "OPERATOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currentPassword"));
        mockMvc.perform(change("{\"currentPassword\":\"Cur#rent1Pass\"}").with(caller("7", "OPERATOR")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(useCase);
    }

    @Test
    void theRulesOfTheUseCaseReachTheClientAs422() throws Exception {
        willThrow(new BusinessRuleException("a senha atual está incorreta")).given(useCase).change(any());

        mockMvc.perform(change(BODY).with(caller("7", "OPERATOR")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("a senha atual está incorreta"));
    }

    @Test
    void anUnknownUserOfTheTokenGets404() throws Exception {
        willThrow(new ResourceNotFoundException("usuário não encontrado: 7")).given(useCase).change(any());

        mockMvc.perform(change(BODY).with(caller("7", "OPERATOR"))).andExpect(status().isNotFound());
    }

    @Test
    void aTokenWithoutAnIdOrWithANonNumericOneIsRefused() throws Exception {
        mockMvc.perform(change(BODY).with(caller("abc", "OPERATOR"))).andExpect(status().isForbidden());

        verifyNoInteractions(useCase);
    }

    @Test
    void aReaderIsRefusedBecauseTheReaderChangesThePasswordInTheReaderArea() throws Exception {
        mockMvc.perform(change(BODY).with(caller("7", "READER"))).andExpect(status().isForbidden());

        verifyNoInteractions(useCase);
    }

    @Test
    void noTokenGets401() throws Exception {
        mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theStringFormOfTheCommandKeepsThePasswordsOut() {
        assertThat(new ChangeStaffPasswordCommand(7L, "Cur#rent1Pass", "N3w-Pass#word").toString())
                .doesNotContain("Cur#rent1Pass").doesNotContain("N3w-Pass#word");
    }
}
