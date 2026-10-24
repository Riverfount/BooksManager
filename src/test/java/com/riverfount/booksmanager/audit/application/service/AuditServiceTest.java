package com.riverfount.booksmanager.audit.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.riverfount.booksmanager.audit.application.port.in.AuditEvent;
import com.riverfount.booksmanager.audit.application.port.in.ListAuditLogQuery;
import com.riverfount.booksmanager.audit.application.port.out.ActorLogins;
import com.riverfount.booksmanager.audit.application.port.out.AuditEntryRepository;
import com.riverfount.booksmanager.audit.application.port.out.AuditFilter;
import com.riverfount.booksmanager.audit.application.port.out.CurrentActor;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditEntry;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final Instant NOW = Instant.parse("2026-10-24T15:00:00Z");

    @Mock
    private AuditEntryRepository repository;

    @Mock
    private CurrentActor currentActor;

    @Mock
    private ActorLogins actorLogins;

    private AuditService service;

    @BeforeEach
    void setUp() {
        service = new AuditService(repository, currentActor, actorLogins, Clock.fixed(NOW, SAO_PAULO));
    }

    private AuditEntry saved() {
        var captor = ArgumentCaptor.forClass(AuditEntry.class);
        then(repository).should().save(captor.capture());
        return captor.getValue();
    }

    @Test
    void shouldRecordTheOperationOfTheAuthenticatedCaller() {
        given(currentActor.id()).willReturn(Optional.of(7L));
        given(actorLogins.loginOf(7L)).willReturn(Optional.of("maria"));

        service.record(new AuditEvent(AuditAction.FINE_WAIVED, AuditTarget.FINE, 42L, "multa abonada: erro"));

        var entry = saved();
        assertThat(entry.occurredAt()).isEqualTo(NOW);
        assertThat(entry.actorId()).isEqualTo(7L);
        assertThat(entry.actorLogin()).isEqualTo("maria");
        assertThat(entry.action()).isEqualTo(AuditAction.FINE_WAIVED);
        assertThat(entry.target()).isEqualTo(AuditTarget.FINE);
        assertThat(entry.targetId()).isEqualTo(42L);
        assertThat(entry.summary()).isEqualTo("multa abonada: erro");
    }

    @Test
    void shouldRecordWithoutActorWhenNobodyIsAuthenticated() {
        given(currentActor.id()).willReturn(Optional.empty());

        service.record(new AuditEvent(AuditAction.USER_CREATED, AuditTarget.USER, 1L, "administrador inicial"));

        assertThat(saved().actorId()).isNull();
        assertThat(saved().actorLogin()).isNull();
        then(actorLogins).shouldHaveNoInteractions();
    }

    @Test
    void shouldKeepTheActorIdEvenWhenItsLoginIsUnknown() {
        given(currentActor.id()).willReturn(Optional.of(7L));
        given(actorLogins.loginOf(7L)).willReturn(Optional.empty());

        service.record(new AuditEvent(AuditAction.READER_BLOCKED, AuditTarget.READER, 3L, null));

        assertThat(saved().actorId()).isEqualTo(7L);
        assertThat(saved().actorLogin()).isNull();
    }

    @Test
    void shouldRecordAsTheGivenUserWithoutLookingAtTheCaller() {
        given(actorLogins.loginOf(9L)).willReturn(Optional.of("joao"));

        service.recordAs(9L, new AuditEvent(AuditAction.LOGIN_SUCCESS, AuditTarget.USER, 9L, null));

        var entry = saved();
        assertThat(entry.actorId()).isEqualTo(9L);
        assertThat(entry.actorLogin()).isEqualTo("joao");
        then(currentActor).shouldHaveNoInteractions();
    }

    @Test
    void shouldRecordAsNobodyWhenTheGivenUserIsNull() {
        service.recordAs(null, new AuditEvent(AuditAction.LOGIN_FAILURE, null, null, "login desconhecido"));

        assertThat(saved().actorId()).isNull();
        then(currentActor).shouldHaveNoInteractions();
        then(actorLogins).shouldHaveNoInteractions();
    }

    @Test
    void shouldListWithTheFiltersAndTheDaysTurnedIntoSaoPauloInstants() {
        var page = new PageResult<AuditEntry>(List.of(), 0, 20, 0);
        given(repository.findAll(any(), any())).willReturn(page);

        var result = service.list(ListAuditLogQuery.of(7L, AuditAction.FINE_WAIVED, AuditTarget.FINE, 42L,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 0, 20));

        assertThat(result).isSameAs(page);
        var filter = ArgumentCaptor.forClass(AuditFilter.class);
        then(repository).should().findAll(filter.capture(), any());
        assertThat(filter.getValue().actorId()).isEqualTo(7L);
        assertThat(filter.getValue().action()).isEqualTo(AuditAction.FINE_WAIVED);
        assertThat(filter.getValue().target()).isEqualTo(AuditTarget.FINE);
        assertThat(filter.getValue().targetId()).isEqualTo(42L);
        // 00:00 of the first day, in São Paulo (UTC-3), up to but not including 00:00 of the day after the last
        assertThat(filter.getValue().from()).isEqualTo(Instant.parse("2026-10-01T03:00:00Z"));
        assertThat(filter.getValue().toExclusive()).isEqualTo(Instant.parse("2026-11-01T03:00:00Z"));
    }

    @Test
    void shouldListWithoutFiltersWhenNoneIsGiven() {
        given(repository.findAll(any(), any())).willReturn(new PageResult<>(List.of(), 0, 20, 0));

        service.list(ListAuditLogQuery.of(null, null, null, null, null, null, 0, 20));

        var filter = ArgumentCaptor.forClass(AuditFilter.class);
        then(repository).should().findAll(filter.capture(), any());
        assertThat(filter.getValue()).isEqualTo(new AuditFilter(null, null, null, null, null, null));
    }

    @Test
    void shouldAskTheNewestFirst() {
        var query = ListAuditLogQuery.of(null, null, null, null, null, null, 2, 50);

        assertThat(query.page().sortBy()).isEqualTo("occurredAt");
        assertThat(query.page().direction().name()).isEqualTo("DESC");
        assertThat(query.page().page()).isEqualTo(2);
        assertThat(query.page().size()).isEqualTo(50);
    }

    @Test
    void anInvertedPeriodShouldBeRefusedBeforeReadingAnything() {
        assertThatThrownBy(() -> ListAuditLogQuery.of(null, null, null, null, LocalDate.of(2026, 10, 31),
                LocalDate.of(2026, 10, 1), 0, 20))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessageContaining("invertido");

        then(repository).should(never()).findAll(any(), any());
    }

    @Test
    void aTargetIdWithoutTheTargetTypeShouldBeRefused() {
        assertThatThrownBy(() -> ListAuditLogQuery.of(null, null, null, 42L, null, null, 0, 20))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessageContaining("targetType");
    }
}
