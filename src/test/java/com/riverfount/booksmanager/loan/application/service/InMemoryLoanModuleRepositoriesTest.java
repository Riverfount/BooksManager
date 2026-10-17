package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.loan.application.port.out.FineFilter;
import com.riverfount.booksmanager.loan.application.port.out.LoanFilter;
import com.riverfount.booksmanager.loan.application.port.out.SuspensionFilter;
import com.riverfount.booksmanager.loan.domain.Fine;
import com.riverfount.booksmanager.loan.domain.FineStatus;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.loan.domain.Suspension;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** The fakes must behave like the adapters, or the service tests that use them would prove nothing. */
class InMemoryLoanModuleRepositoriesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 1);

    private final InMemoryLoanRepository loans = new InMemoryLoanRepository();
    private final InMemoryFineRepository fines = new InMemoryFineRepository();
    private final InMemorySuspensionRepository suspensions = new InMemorySuspensionRepository();

    // loans

    @Test
    void shouldGiveAnIdToANewLoanAndFindIt() {
        var saved = loans.save(Loan.create(1L, 10L, DAY, 14));

        assertThat(saved.getId()).isNotNull();
        assertThat(loans.findById(saved.getId())).isPresent();
        assertThat(loans.findById(-1L)).isEmpty();
    }

    @Test
    void aChangeToALoanOnlyTakesEffectWhenItIsSaved() {
        var loan = loans.save(Loan.create(1L, 10L, DAY, 14));
        loan.returnCopy(DAY.plusDays(3));

        assertThat(loans.findById(loan.getId()).orElseThrow().isActive()).isTrue();

        loans.save(loan);

        assertThat(loans.findById(loan.getId()).orElseThrow().isReturned()).isTrue();
    }

    @Test
    void shouldRefuseASecondActiveLoanOfTheSameCopy() {
        loans.save(Loan.create(1L, 10L, DAY, 14));

        assertThatThrownBy(() -> loans.save(Loan.create(1L, 11L, DAY, 14)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldAllowANewLoanOfTheCopyOnceItWasReturned() {
        var first = loans.save(Loan.create(1L, 10L, DAY, 14));
        first.returnCopy(DAY.plusDays(2));
        loans.save(first);

        assertThat(loans.save(Loan.create(1L, 11L, DAY.plusDays(3), 14)).getId()).isNotEqualTo(first.getId());
    }

    @Test
    void shouldFilterSortAndPaginateLoans() {
        for (var day = 0; day < 3; day++) {
            loans.save(Loan.create(1L + day, 10L, DAY.plusDays(day), 14));
        }
        loans.save(Loan.create(9L, 11L, DAY, 14));

        var page = loans.findAll(new LoanFilter(10L, null, true, null), PageQuery.of(0, 2, "pickupDate,desc"));

        assertThat(page.items()).extracting(Loan::getPickupDate).containsExactly(DAY.plusDays(2), DAY.plusDays(1));
        assertThat(page.totalItems()).isEqualTo(3);
    }

    @Test
    void shouldFilterTheOverdueLoans() {
        var late = loans.save(Loan.create(1L, 10L, DAY.minusDays(20), 14));
        loans.save(Loan.create(2L, 10L, DAY, 14));
        var returned = loans.save(Loan.create(3L, 10L, DAY.minusDays(20), 14));
        returned.returnCopy(DAY);
        loans.save(returned);

        var page = loans.findAll(new LoanFilter(10L, null, null, DAY), PageQuery.of(0, 20, "id"));

        assertThat(page.items()).extracting(Loan::getId).containsExactly(late.getId());
    }

    // fines

    @Test
    void shouldRefuseASecondFineForTheSameLoan() {
        fines.save(Fine.create(1L, 10L, new BigDecimal("3.00")));

        assertThatThrownBy(() -> fines.save(Fine.create(1L, 10L, new BigDecimal("4.00"))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldSaveAPaymentAndFilterFinesBySituation() {
        var paid = fines.save(Fine.create(1L, 10L, new BigDecimal("3.00")));
        paid.pay();
        fines.save(paid);
        var pending = fines.save(Fine.create(2L, 10L, new BigDecimal("4.00")));

        var page = fines.findAll(new FineFilter(10L, null, FineStatus.PENDING), PageQuery.of(0, 20, "id"));

        assertThat(page.items()).extracting(Fine::getId).containsExactly(pending.getId());
    }

    // suspensions

    @Test
    void shouldFilterTheSuspensionsInForceOnADay() {
        var running = suspensions.save(Suspension.create(10L, 1L, DAY, 5));
        var endedEarly = suspensions.save(Suspension.create(10L, 2L, DAY, 5));
        endedEarly.endEarly(DAY.plusDays(1), "Pardoned");
        suspensions.save(endedEarly);

        var inForce = suspensions.findAll(new SuspensionFilter(10L, null, DAY.plusDays(2), null),
                PageQuery.of(0, 20, "id"));

        assertThat(inForce.items()).extracting(Suspension::getId).containsExactly(running.getId());
        assertThat(suspensions.findById(endedEarly.getId()).orElseThrow().isEndedEarly()).isTrue();
    }

    @Test
    void shouldFilterTheSuspensionsNotInForceOnADay() {
        suspensions.save(Suspension.create(10L, 1L, DAY, 5));
        var endedEarly = suspensions.save(Suspension.create(10L, 2L, DAY, 5));
        endedEarly.endEarly(DAY.plusDays(1), "Pardoned");
        suspensions.save(endedEarly);

        var notInForce = suspensions.findAll(new SuspensionFilter(10L, null, null, DAY.plusDays(2)),
                PageQuery.of(0, 20, "id"));

        assertThat(notInForce.items()).extracting(Suspension::getId).containsExactly(endedEarly.getId());
    }

    @Test
    void shouldFindTheFinesAndSuspensionsOfSeveralLoans() {
        var fine = fines.save(Fine.create(1L, 10L, new BigDecimal("3.00")));
        var suspension = suspensions.save(Suspension.create(10L, 2L, DAY, 5));
        var latest = suspensions.save(Suspension.create(10L, 2L, DAY, 9));

        var foundFines = fines.findByLoanIds(java.util.Set.of(1L, 2L, 3L));
        assertThat(foundFines).containsOnlyKeys(1L);
        assertThat(foundFines.get(1L).getId()).isEqualTo(fine.getId());
        assertThat(suspensions.findByLoanIds(java.util.Set.of(1L, 2L, 3L))).containsOnlyKeys(2L);
        assertThat(suspensions.findByLoanIds(java.util.Set.of(2L)).get(2L).getId())
                .isEqualTo(latest.getId()).isNotEqualTo(suspension.getId());
        assertThat(fines.findByLoanIds(java.util.Set.of())).isEmpty();
        assertThat(suspensions.findByLoanIds(java.util.Set.of())).isEmpty();
    }
}
