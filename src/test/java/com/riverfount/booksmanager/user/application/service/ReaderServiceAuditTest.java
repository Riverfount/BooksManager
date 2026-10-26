package com.riverfount.booksmanager.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.audit.application.RecordedAudit;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.user.application.port.in.RegisterReaderCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** What blocking, unblocking and deactivating a reader report to the audit trail (RF27, RN14). */
class ReaderServiceAuditTest {

    private InMemoryReaderRepository repository;
    private RecordedAudit audit;
    private ReaderService service;
    private final java.util.List<Long> revokedAccessOf = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        repository = new InMemoryReaderRepository();
        audit = new RecordedAudit();
        service = new ReaderService(repository, Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC),
                audit, revokedAccessOf::add);
    }

    private Long reader() {
        return service.register(new RegisterReaderCommand("Maria Silva", "123.456.789-00", "maria@example.com", null,
                LocalDate.of(1990, 5, 10), null, null, null)).getId();
    }

    @Test
    void registeringAReaderIsNotAuditedAndNeverReportsPersonalData() {
        reader();

        assertThat(audit.isEmpty()).isTrue();
    }

    @Test
    void shouldReportTheBlockAsDoneByTheCallerWithoutPersonalData() {
        var id = reader();

        service.block(id);

        var recorded = audit.all().getFirst();
        assertThat(audit.all()).hasSize(1);
        assertThat(recorded.asCaller()).isTrue();
        assertThat(recorded.event().action()).isEqualTo(AuditAction.READER_BLOCKED);
        assertThat(recorded.event().target()).isEqualTo(AuditTarget.READER);
        assertThat(recorded.event().targetId()).isEqualTo(id);
        assertThat(recorded.event().summary()).isNull();
        assertThat(audit.all().toString()).doesNotContain("Maria").doesNotContain("12345678900")
                .doesNotContain("maria@example.com");
    }

    @Test
    void shouldReportTheUnblockOfABlockedReaderOnly() {
        var id = reader();
        service.unblock(id);
        assertThat(audit.isEmpty()).isTrue();

        service.block(id);
        service.unblock(id);

        assertThat(audit.actions()).containsExactly(AuditAction.READER_BLOCKED, AuditAction.READER_UNBLOCKED);
        assertThat(audit.events().getLast().targetId()).isEqualTo(id);
    }

    @Test
    void blockingAReaderThatIsAlreadyBlockedShouldReportItOnce() {
        var id = reader();

        service.block(id);
        service.block(id);

        assertThat(audit.actions()).containsExactly(AuditAction.READER_BLOCKED);
    }

    @Test
    void shouldReportTheDeactivationOnce() {
        var id = reader();

        service.deactivate(id);
        service.deactivate(id);

        assertThat(audit.actions()).containsExactly(AuditAction.READER_DEACTIVATED);
        assertThat(audit.only().targetId()).isEqualTo(id);
    }

    @Test
    void shouldReportNothingForAReaderThatDoesNotExist() {
        assertThatThrownBy(() -> service.block(999L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.unblock(999L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deactivate(999L)).isInstanceOf(ResourceNotFoundException.class);

        assertThat(audit.isEmpty()).isTrue();
    }

    @Test
    void deactivatingAReaderShouldTakeAwayTheAccessOfTheReader() {
        var id = reader();

        service.deactivate(id);

        assertThat(revokedAccessOf).containsExactly(id);
    }

    @Test
    void deactivatingAnAlreadyInactiveReaderShouldStillMakeSureTheAccessIsGone() {
        var id = reader();
        service.deactivate(id);

        service.deactivate(id);

        assertThat(revokedAccessOf).containsExactly(id, id);
    }

    @Test
    void blockingAReaderShouldNotTouchTheAccess() {
        service.block(reader());

        assertThat(revokedAccessOf).isEmpty();
    }
}
