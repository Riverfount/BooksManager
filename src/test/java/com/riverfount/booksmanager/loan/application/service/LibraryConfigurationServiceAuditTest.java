package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.audit.application.RecordedAudit;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.loan.application.port.in.UpdateLibraryConfigurationCommand;
import com.riverfount.booksmanager.loan.domain.PenaltyPolicy;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** What changing the library configuration reports to the audit trail (RF27): who changed the penalty policy. */
class LibraryConfigurationServiceAuditTest {

    private RecordedAudit audit;
    private LibraryConfigurationService service;

    @BeforeEach
    void setUp() {
        audit = new RecordedAudit();
        service = new LibraryConfigurationService(new InMemoryLibraryConfigurationRepository(), audit);
    }

    private static UpdateLibraryConfigurationCommand only(PenaltyPolicy policy, Integer period) {
        return new UpdateLibraryConfigurationCommand(policy, null, null, null, null, period, null, null, null, null);
    }

    @Test
    void shouldReportTheChangeWithOnlyTheValuesThatWereSet() {
        service.update(only(PenaltyPolicy.BOTH, 21));

        var recorded = audit.all().getFirst();
        assertThat(audit.all()).hasSize(1);
        assertThat(recorded.asCaller()).isTrue();
        assertThat(recorded.event().action()).isEqualTo(AuditAction.LIBRARY_CONFIGURATION_UPDATED);
        assertThat(recorded.event().target()).isEqualTo(AuditTarget.LIBRARY_CONFIGURATION);
        assertThat(recorded.event().targetId()).isNull();
        assertThat(recorded.event().summary()).isEqualTo("penaltyPolicy=BOTH, loanPeriodDays=21");
    }

    @Test
    void shouldListEveryValueOfAFullUpdateWithPlainAmounts() {
        service.update(new UpdateLibraryConfigurationCommand(PenaltyPolicy.FINE, new BigDecimal("1.50"),
                new BigDecimal("1E+1"), 2, 100, 14, 3, 2, 3, 2));

        assertThat(audit.only().summary()).isEqualTo("penaltyPolicy=FINE, dailyFineAmount=1.50, fineCap=10, "
                + "suspensionDaysPerDay=2, suspensionCapDays=100, loanPeriodDays=14, loanLimit=3, renewalLimit=2, "
                + "reservationPickupDays=3, dueSoonNoticeDays=2");
    }

    @Test
    void anUpdateThatSetsNothingShouldStillBeReportedWithoutASummary() {
        service.update(only(null, null));

        assertThat(audit.only().action()).isEqualTo(AuditAction.LIBRARY_CONFIGURATION_UPDATED);
        assertThat(audit.only().summary()).isNull();
    }

    @Test
    void shouldReportNothingWhenTheDomainRefusesAValue() {
        assertThatThrownBy(() -> service.update(only(null, 0))).isInstanceOf(BusinessRuleException.class);

        assertThat(audit.isEmpty()).isTrue();
    }
}
