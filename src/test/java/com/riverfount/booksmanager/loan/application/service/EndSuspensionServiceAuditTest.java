package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.audit.application.RecordedAudit;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.loan.application.port.in.EndSuspensionCommand;
import com.riverfount.booksmanager.loan.domain.Suspension;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** What ending a suspension early reports to the audit trail (RF27): who ended it, and the justification. */
class EndSuspensionServiceAuditTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private InMemorySuspensionRepository suspensionRepository;
    private RecordedAudit audit;
    private EndSuspensionService service;

    @BeforeEach
    void setUp() {
        suspensionRepository = new InMemorySuspensionRepository();
        audit = new RecordedAudit();
        service = new EndSuspensionService(suspensionRepository, CLOCK, audit);
    }

    private Long suspension(int daysAgo, int days) {
        return suspensionRepository.save(Suspension.create(20L, 1L, TODAY.minusDays(daysAgo), days)).getId();
    }

    @Test
    void shouldReportTheEarlyEndWithTheJustification() {
        var id = suspension(1, 10);

        service.end(new EndSuspensionCommand(id, "  Pardoned by the librarian  "));

        var recorded = audit.all().getFirst();
        assertThat(audit.all()).hasSize(1);
        assertThat(recorded.asCaller()).isTrue();
        assertThat(recorded.event().action()).isEqualTo(AuditAction.SUSPENSION_ENDED);
        assertThat(recorded.event().target()).isEqualTo(AuditTarget.SUSPENSION);
        assertThat(recorded.event().targetId()).isEqualTo(id);
        assertThat(recorded.event().summary()).isEqualTo("encerrada antes do prazo: Pardoned by the librarian");
    }

    @Test
    void shouldReportNothingWhenThereWasNothingToEnd() {
        var alreadyOver = suspension(10, 3); // over since 7 days ago
        var endsToday = suspension(3, 3); // free again today

        service.end(new EndSuspensionCommand(alreadyOver, "late request"));
        service.end(new EndSuspensionCommand(endsToday, "late request"));

        assertThat(audit.isEmpty()).isTrue();
    }

    @Test
    void shouldReportItOnlyTheFirstTimeItIsEnded() {
        var id = suspension(1, 10);

        service.end(new EndSuspensionCommand(id, "first"));
        service.end(new EndSuspensionCommand(id, "second"));

        assertThat(audit.only().summary()).isEqualTo("encerrada antes do prazo: first");
    }

    @Test
    void shouldReportNothingWhenTheRequestIsRefused() {
        var id = suspension(1, 10);

        assertThatThrownBy(() -> service.end(new EndSuspensionCommand(id, "   ")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.end(new EndSuspensionCommand(999L, "reason")))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(audit.isEmpty()).isTrue();
    }
}
