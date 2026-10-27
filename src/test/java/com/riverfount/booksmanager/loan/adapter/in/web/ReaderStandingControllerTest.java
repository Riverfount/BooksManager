package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.riverfount.booksmanager.WithAdmin;
import com.riverfount.booksmanager.WithOperator;
import com.riverfount.booksmanager.loan.application.port.in.GetOwnStandingQuery;
import com.riverfount.booksmanager.loan.application.port.in.GetOwnStandingUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ReaderStanding;
import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The standing of the reader (Phase 6): the reader is the one of the token, never one sent in the request, the answer
 * has exactly the fields of the contract and nothing of anyone else, and the administrative profiles are kept out.
 */
@WebMvcTest(ReaderStandingController.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class})
class ReaderStandingControllerTest {

    private static final long ME = 42L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetOwnStandingUseCase getUseCase;

    private static RequestPostProcessor reader() {
        return jwt().jwt(token -> token.subject("5").claim("readerId", ME))
                .authorities(new SimpleGrantedAuthority("ROLE_READER"));
    }

    private static ReaderStanding goodStanding() {
        return new ReaderStanding(true, false, List.of(), BigDecimal.ZERO, null, 1, 0, 3, 0, 3);
    }

    private static ReaderStanding troubledStanding() {
        return new ReaderStanding(false, true, List.of("o leitor está bloqueado", "o leitor tem multa pendente"),
                new BigDecimal("6.50"),
                new ReaderStanding.SuspensionInForce(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 14)),
                2, 1, 3, 1, 3);
    }

    @Test
    void shouldShowAReaderInGoodStanding() throws Exception {
        given(getUseCase.get(any())).willReturn(goodStanding());

        mockMvc.perform(get("/api/v1/me/standing").with(reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canBorrow").value(true))
                .andExpect(jsonPath("$.blocked").value(false))
                .andExpect(jsonPath("$.reasons").isEmpty())
                .andExpect(jsonPath("$.pendingFineTotal").value(0))
                .andExpect(jsonPath("$.suspension").doesNotExist())
                .andExpect(jsonPath("$.activeLoans").value(1))
                .andExpect(jsonPath("$.loanLimit").value(3))
                .andExpect(jsonPath("$.reservationLimit").value(3));
    }

    @Test
    void shouldShowEveryReasonTheTotalAndTheSuspension() throws Exception {
        given(getUseCase.get(any())).willReturn(troubledStanding());

        var body = mockMvc.perform(get("/api/v1/me/standing").with(reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canBorrow").value(false))
                .andExpect(jsonPath("$.blocked").value(true))
                .andExpect(jsonPath("$.reasons.length()").value(2))
                .andExpect(jsonPath("$.reasons[0]").value("o leitor está bloqueado"))
                .andExpect(jsonPath("$.pendingFineTotal").value(6.50))
                .andExpect(jsonPath("$.suspension.startDate").value("2026-11-10"))
                .andExpect(jsonPath("$.suspension.releaseDate").value("2026-11-14"))
                .andExpect(jsonPath("$.overdueLoans").value(1))
                .andExpect(jsonPath("$.activeReservations").value(1))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> standing = JsonPath.read(body, "$");
        assertThat(standing.keySet()).containsExactlyInAnyOrder("canBorrow", "blocked", "reasons",
                "pendingFineTotal", "suspension", "activeLoans", "overdueLoans", "loanLimit", "activeReservations",
                "reservationLimit");
    }

    @Test
    void shouldAskForTheReaderOfTheTokenAndIgnoreOneSentInTheRequest() throws Exception {
        given(getUseCase.get(any())).willReturn(goodStanding());

        mockMvc.perform(get("/api/v1/me/standing").with(reader()).param("readerId", "99"))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(GetOwnStandingQuery.class);
        then(getUseCase).should().get(captor.capture());
        assertThat(captor.getValue().readerId()).isEqualTo(ME);
    }

    @Test
    @WithAdmin
    void anAdministratorMayNotUseTheStandingOfTheReader() throws Exception {
        mockMvc.perform(get("/api/v1/me/standing")).andExpect(status().isForbidden());
        verifyNoInteractions(getUseCase);
    }

    @Test
    @WithOperator
    void anOperatorMayNotUseTheStandingOfTheReader() throws Exception {
        mockMvc.perform(get("/api/v1/me/standing")).andExpect(status().isForbidden());
        verifyNoInteractions(getUseCase);
    }

    @Test
    void anonymousIs401() throws Exception {
        mockMvc.perform(get("/api/v1/me/standing")).andExpect(status().isUnauthorized());
    }
}
