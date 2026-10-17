package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.riverfount.booksmanager.catalog.application.port.in.CatalogSummary;
import com.riverfount.booksmanager.catalog.application.port.in.CategoryCopies;
import com.riverfount.booksmanager.catalog.application.port.in.GetCatalogSummaryUseCase;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.loan.domain.Suspension;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The dashboard numbers (RF23, RF25): the active copies and the by category counts come from the catalog's
 * public port; the loans and the suspensions are the loan module's, counted as the listings count them.
 */
class DashboardServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-20T15:00:00Z"), ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    private InMemoryLoanRepository loanRepository;
    private InMemorySuspensionRepository suspensionRepository;
    private GetCatalogSummaryUseCase catalog;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        loanRepository = new InMemoryLoanRepository();
        suspensionRepository = new InMemorySuspensionRepository();
        catalog = mock(GetCatalogSummaryUseCase.class);
        given(catalog.get()).willReturn(new CatalogSummary(0, List.of()));
        service = new DashboardService(loanRepository, suspensionRepository, catalog, CLOCK);
    }

    private Loan openLoanDueOn(long copy, long reader, LocalDate due) {
        return loanRepository.save(Loan.create(copy, reader, due.minusDays(14), 14));
    }

    @Test
    void shouldTakeTheCopiesAndTheCategoriesFromTheCatalog() {
        var byCategory = List.of(new CategoryCopies(1L, "Ficção", 3, 7), new CategoryCopies(2L, "Poesia", 1, 2));
        given(catalog.get()).willReturn(new CatalogSummary(9, byCategory));

        var dashboard = service.get();

        assertThat(dashboard.totalCopies()).isEqualTo(9L);
        assertThat(dashboard.copiesByCategory()).isEqualTo(byCategory);
    }

    @Test
    void shouldCountTheActiveLoansAndNotTheReturnedOnes() {
        openLoanDueOn(1L, 10L, TODAY.plusDays(5));
        openLoanDueOn(2L, 11L, TODAY.minusDays(3));
        var returned = openLoanDueOn(3L, 12L, TODAY.plusDays(1));
        returned.returnCopy(TODAY);
        loanRepository.save(returned);

        assertThat(service.get().activeLoans()).isEqualTo(2L);
    }

    @Test
    void shouldCountTheOverdueLoansAsTheReportDoesWithADueTodayStillOnTime() {
        openLoanDueOn(1L, 10L, TODAY.minusDays(1));
        openLoanDueOn(2L, 11L, TODAY);
        openLoanDueOn(3L, 12L, TODAY.plusDays(2));

        var dashboard = service.get();

        assertThat(dashboard.overdueLoans()).isEqualTo(1L);
        assertThat(dashboard.activeLoans()).isEqualTo(3L);
    }

    @Test
    void shouldCountSuspendedReadersNotSuspensions() {
        // the same reader with two suspensions in force at once counts once
        suspensionRepository.save(Suspension.create(10L, 100L, TODAY.minusDays(1), 5));
        suspensionRepository.save(Suspension.create(10L, 101L, TODAY, 5));
        suspensionRepository.save(Suspension.create(11L, 102L, TODAY.minusDays(2), 5));

        assertThat(service.get().suspendedReaders()).isEqualTo(2L);
    }

    @Test
    void aSuspensionAlreadyOverOrNotStartedShouldNotCount() {
        suspensionRepository.save(Suspension.create(10L, 100L, TODAY.minusDays(10), 3));
        suspensionRepository.save(Suspension.create(11L, 101L, TODAY.plusDays(2), 3));
        var endedEarly = suspensionRepository.save(Suspension.create(12L, 102L, TODAY.minusDays(1), 10));
        endedEarly.endEarly(TODAY, "Perdão");
        suspensionRepository.save(endedEarly);

        assertThat(service.get().suspendedReaders()).isZero();
    }

    @Test
    void anEmptyLibraryShouldGiveZeros() {
        var dashboard = service.get();

        assertThat(dashboard.totalCopies()).isZero();
        assertThat(dashboard.activeLoans()).isZero();
        assertThat(dashboard.overdueLoans()).isZero();
        assertThat(dashboard.suspendedReaders()).isZero();
        assertThat(dashboard.copiesByCategory()).isEmpty();
    }
}
