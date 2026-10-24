package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.audit.application.RecordedAudit;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.loan.application.port.in.WaiveFineCommand;
import com.riverfount.booksmanager.loan.domain.Fine;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** What paying and waiving a fine report to the audit trail (RF27): who waived this fine, and why. */
class FineServiceAuditTest {

    private InMemoryFineRepository fineRepository;
    private RecordedAudit audit;
    private FineService service;

    @BeforeEach
    void setUp() {
        fineRepository = new InMemoryFineRepository();
        audit = new RecordedAudit();
        service = new FineService(fineRepository, audit);
    }

    private Long pendingFine() {
        return fineRepository.save(Fine.create(1L, 20L, new BigDecimal("6.00"))).getId();
    }

    @Test
    void shouldReportThePaymentAsDoneByTheCaller() {
        var id = pendingFine();

        service.pay(id);

        var recorded = audit.all().getFirst();
        assertThat(audit.all()).hasSize(1);
        assertThat(recorded.asCaller()).isTrue();
        assertThat(recorded.event().action()).isEqualTo(AuditAction.FINE_PAID);
        assertThat(recorded.event().target()).isEqualTo(AuditTarget.FINE);
        assertThat(recorded.event().targetId()).isEqualTo(id);
    }

    @Test
    void shouldReportTheWaiverWithTheJustification() {
        var id = pendingFine();

        service.waive(new WaiveFineCommand(id, "  Hospital stay  "));

        var recorded = audit.all().getFirst();
        assertThat(audit.all()).hasSize(1);
        assertThat(recorded.asCaller()).isTrue();
        assertThat(recorded.event().action()).isEqualTo(AuditAction.FINE_WAIVED);
        assertThat(recorded.event().targetId()).isEqualTo(id);
        assertThat(recorded.event().summary()).isEqualTo("abono: Hospital stay");
    }

    @Test
    void shouldReportNothingWhenThePaymentIsRefused() {
        var id = pendingFine();
        service.pay(id);
        var before = audit.all().size();

        assertThatThrownBy(() -> service.pay(id)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.pay(999L)).isInstanceOf(ResourceNotFoundException.class);

        assertThat(audit.all()).hasSize(before);
    }

    @Test
    void shouldReportNothingWhenTheWaiverIsRefused() {
        var id = pendingFine();

        assertThatThrownBy(() -> service.waive(new WaiveFineCommand(id, "   ")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.waive(new WaiveFineCommand(999L, "reason")))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(audit.isEmpty()).isTrue();
    }
}
