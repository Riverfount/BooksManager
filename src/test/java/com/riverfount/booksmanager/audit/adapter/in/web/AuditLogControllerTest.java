package com.riverfount.booksmanager.audit.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.WithAdmin;
import com.riverfount.booksmanager.WithOperator;
import com.riverfount.booksmanager.audit.application.port.in.ListAuditLogQuery;
import com.riverfount.booksmanager.audit.application.port.in.ListAuditLogUseCase;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditEntry;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuditLogController.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class})
class AuditLogControllerTest {

    private static final String URL = "/api/v1/audit-log";
    private static final Instant AT = Instant.parse("2026-10-24T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListAuditLogUseCase listAuditLogUseCase;

    private static AuditEntry entry(long id) {
        return AuditEntry.reconstitute(id, AT, 7L, "maria", AuditAction.FINE_WAIVED, AuditTarget.FINE, 42L,
                "multa abonada: erro de cálculo");
    }

    // access: the audit trail is for the administrator only (RF27, RNF08)

    @Test
    @WithAdmin
    void anAdministratorShouldReadTheTrail() throws Exception {
        given(listAuditLogUseCase.list(any())).willReturn(new PageResult<>(List.of(entry(1)), 0, 20, 1));

        mockMvc.perform(get(URL)).andExpect(status().isOk());
    }

    @Test
    @WithOperator
    void anOperatorShouldAnswer403AndNothingShouldBeRead() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        then(listAuditLogUseCase).shouldHaveNoInteractions();
    }

    @Test
    @WithOperator
    void anOperatorShouldNotGetAroundItWithFiltersOrAnIdInThePath() throws Exception {
        mockMvc.perform(get(URL).param("actorId", "7").param("action", "FINE_WAIVED")).andExpect(status().isForbidden());
        mockMvc.perform(get(URL + "/1")).andExpect(status().is4xxClientError());

        then(listAuditLogUseCase).shouldHaveNoInteractions();
    }

    @Test
    void anAnonymousRequestShouldAnswer401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());

        then(listAuditLogUseCase).shouldHaveNoInteractions();
    }

    // content

    @Test
    @WithAdmin
    void shouldShowWhoWhenWhatAndOnWhichTargetOfEachEntry() throws Exception {
        given(listAuditLogUseCase.list(any())).willReturn(new PageResult<>(List.of(entry(5), entry(4)), 0, 20, 2));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(5))
                .andExpect(jsonPath("$.items[0].occurredAt").value("2026-10-24T15:00:00Z"))
                .andExpect(jsonPath("$.items[0].actorId").value(7))
                .andExpect(jsonPath("$.items[0].actorLogin").value("maria"))
                .andExpect(jsonPath("$.items[0].action").value("FINE_WAIVED"))
                .andExpect(jsonPath("$.items[0].targetType").value("FINE"))
                .andExpect(jsonPath("$.items[0].targetId").value(42))
                .andExpect(jsonPath("$.items[0].summary").value("multa abonada: erro de cálculo"))
                .andExpect(jsonPath("$.items[1].id").value(4))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @WithAdmin
    void anEntryWithoutActorAndTargetShouldComeWithNulls() throws Exception {
        var anonymous = AuditEntry.reconstitute(9L, AT, null, null, AuditAction.LOGIN_FAILURE, null, null,
                "login desconhecido");
        given(listAuditLogUseCase.list(any())).willReturn(new PageResult<>(List.of(anonymous), 0, 20, 1));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].actorId").doesNotExist())
                .andExpect(jsonPath("$.items[0].targetType").doesNotExist())
                .andExpect(jsonPath("$.items[0].summary").value("login desconhecido"));
    }

    @Test
    @WithAdmin
    void shouldNeverShowASecret() throws Exception {
        given(listAuditLogUseCase.list(any())).willReturn(new PageResult<>(List.of(entry(1)), 0, 20, 1));

        mockMvc.perform(get(URL))
                .andExpect(jsonPath("$.items[0].password").doesNotExist())
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.items[0].token").doesNotExist());
    }

    // filters and page

    @Test
    @WithAdmin
    void shouldPassTheFiltersAndThePageToTheUseCase() throws Exception {
        given(listAuditLogUseCase.list(any())).willReturn(new PageResult<>(List.of(), 2, 50, 0));

        mockMvc.perform(get(URL).param("actorId", "7").param("action", "FINE_WAIVED").param("targetType", "FINE")
                        .param("targetId", "42").param("from", "2026-10-01").param("to", "2026-10-31")
                        .param("page", "2").param("size", "50"))
                .andExpect(status().isOk());

        var query = ArgumentCaptor.forClass(ListAuditLogQuery.class);
        then(listAuditLogUseCase).should().list(query.capture());
        assertThat(query.getValue().actorId()).isEqualTo(7L);
        assertThat(query.getValue().action()).isEqualTo(AuditAction.FINE_WAIVED);
        assertThat(query.getValue().target()).isEqualTo(AuditTarget.FINE);
        assertThat(query.getValue().targetId()).isEqualTo(42L);
        assertThat(query.getValue().from()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(query.getValue().to()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(query.getValue().page().page()).isEqualTo(2);
        assertThat(query.getValue().page().size()).isEqualTo(50);
    }

    @Test
    @WithAdmin
    void shouldUseTheFirstPageOf20WhenNothingIsAsked() throws Exception {
        given(listAuditLogUseCase.list(any())).willReturn(new PageResult<>(List.of(), 0, 20, 0));

        mockMvc.perform(get(URL)).andExpect(status().isOk());

        var query = ArgumentCaptor.forClass(ListAuditLogQuery.class);
        then(listAuditLogUseCase).should().list(query.capture());
        assertThat(query.getValue().page().page()).isZero();
        assertThat(query.getValue().page().size()).isEqualTo(20);
        assertThat(query.getValue().actorId()).isNull();
    }

    @Test
    @WithAdmin
    void aSizeOf100ShouldBeAcceptedAndAboveThatShouldAnswer400() throws Exception {
        given(listAuditLogUseCase.list(any())).willReturn(new PageResult<>(List.of(), 0, 100, 0));

        mockMvc.perform(get(URL).param("size", "100")).andExpect(status().isOk());
        mockMvc.perform(get(URL).param("size", "101")).andExpect(status().isBadRequest());
    }

    @Test
    @WithAdmin
    void anInvalidFilterShouldAnswer400() throws Exception {
        mockMvc.perform(get(URL).param("action", "DROP_EVERYTHING")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("targetType", "PLANET")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("actorId", "abc")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("from", "ontem")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("targetId", "42")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("from", "2026-10-31").param("to", "2026-10-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("page", "-1")).andExpect(status().isBadRequest());

        then(listAuditLogUseCase).shouldHaveNoInteractions();
    }
}
