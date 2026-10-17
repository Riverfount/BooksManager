package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.loan.application.port.in.RenewLoanCommand;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Renewing a loan (RF13, HU09): the service only orchestrates; the refusals (closed loan,
 * late loan, renewal limit) are the domain's (RN07, RN09), and the limit and the period
 * come from the configuration (RN16).
 */
class RenewLoanServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private InMemoryLibraryConfigurationRepository configurationRepository;
    private InMemoryLoanRepository loanRepository;
    private RenewLoanService service;

    @BeforeEach
    void setUp() {
        configurationRepository = new InMemoryLibraryConfigurationRepository();
        loanRepository = new InMemoryLoanRepository();
        service = new RenewLoanService(configurationRepository, loanRepository, CLOCK);
    }

    /** An active loan picked up {@code daysAgo} days ago for 14 days. */
    private Long openLoan(int daysAgo) {
        return loanRepository.save(Loan.create(7L, 20L, TODAY.minusDays(daysAgo), 14)).getId();
    }

    private void configure(Integer loanPeriodDays, Integer renewalLimit) {
        var configuration = configurationRepository.getForUpdate();
        configuration.update(null, null, null, null, null, loanPeriodDays, null, renewalLimit, null);
        configurationRepository.save(configuration);
    }

    private Loan renew(Long id) {
        return service.renew(new RenewLoanCommand(id));
    }

    @Test
    void shouldMoveTheDueDateForwardByThePeriodAndCountTheRenewal() {
        var id = openLoan(10); // due in 4 days

        var loan = renew(id);

        assertThat(loan.getDueDate()).isEqualTo(TODAY.plusDays(4).plusDays(14));
        assertThat(loan.getRenewals()).isEqualTo(1);
        assertThat(loanRepository.findById(id).orElseThrow().getRenewals()).isEqualTo(1);
        assertThat(loanRepository.findById(id).orElseThrow().getDueDate()).isEqualTo(loan.getDueDate());
    }

    @Test
    void shouldTakeThePeriodAndTheLimitFromTheConfiguration() {
        configure(7, 1);
        var id = openLoan(10);

        assertThat(renew(id).getDueDate()).isEqualTo(TODAY.plusDays(4).plusDays(7));
        assertThatThrownBy(() -> renew(id)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("limite de renovações (1)");
    }

    @Test
    void shouldRefuseRenewingAboveTheLimit() {
        var id = openLoan(5);
        renew(id);
        renew(id); // the configured limit is 2

        assertThatThrownBy(() -> renew(id)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("limite de renovações (2)");
        assertThat(loanRepository.findById(id).orElseThrow().getRenewals()).isEqualTo(2);
    }

    @Test
    void aLimitOfZeroShouldRefuseEveryRenewal() {
        configure(null, 0);

        assertThatThrownBy(() -> renew(openLoan(5))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldRefuseALateLoanAndChangeNothing() {
        var id = openLoan(20); // due 6 days ago

        assertThatThrownBy(() -> renew(id)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("em atraso");

        var stored = loanRepository.findById(id).orElseThrow();
        assertThat(stored.getRenewals()).isZero();
        assertThat(stored.getDueDate()).isEqualTo(TODAY.minusDays(6));
    }

    @Test
    void shouldAllowRenewingOnTheDueDate() {
        assertThat(renew(openLoan(14)).getRenewals()).isEqualTo(1);
    }

    @Test
    void shouldRefuseAClosedLoan() {
        var id = openLoan(5);
        var loan = loanRepository.findById(id).orElseThrow();
        loan.returnCopy(TODAY);
        loanRepository.save(loan);

        assertThatThrownBy(() -> renew(id)).isInstanceOf(BusinessRuleException.class);
        assertThat(loanRepository.findById(id).orElseThrow().getRenewals()).isZero();
    }

    @Test
    void shouldFailWithNotFoundForALoanThatDoesNotExist() {
        assertThatThrownBy(() -> renew(999L)).isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void shouldUseTheDayOfTheClockInTheLibraryTimezone() {
        // 01:00 UTC of the 25th is still the 24th in São Paulo, the day the loan is due: on time
        var lateNight = Clock.fixed(Instant.parse("2026-11-25T01:00:00Z"), ZONE);
        var lateService = new RenewLoanService(configurationRepository, loanRepository, lateNight);
        var id = loanRepository.save(Loan.create(7L, 20L, LocalDate.of(2026, 11, 10), 14)).getId();

        assertThat(lateService.renew(new RenewLoanCommand(id)).getRenewals()).isEqualTo(1);
    }
}
