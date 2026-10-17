package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.riverfount.booksmanager.catalog.application.port.in.CopySummary;
import com.riverfount.booksmanager.catalog.application.port.in.FindCopySummariesUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ListOverdueLoansQuery;
import com.riverfount.booksmanager.loan.application.port.in.OverdueLoan;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.user.application.port.in.FindReaderNamesUseCase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The overdue loans report (RF23, HU12): the active loans past their due date, from the most to
 * the least late, each with the reader, the copy and the days late. The lateness is the domain's
 * ({@code Loan.isOverdue}, {@code Loan.daysLate}); the reader and the copy come from the public
 * ports of their modules, in one call each for the whole page.
 */
class OverdueLoanQueryServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private InMemoryLoanRepository loanRepository;
    private FindReaderNamesUseCase readerNames;
    private FindCopySummariesUseCase copySummaries;
    private OverdueLoanQueryService service;

    @BeforeEach
    void setUp() {
        loanRepository = new InMemoryLoanRepository();
        readerNames = mock(FindReaderNamesUseCase.class);
        copySummaries = mock(FindCopySummariesUseCase.class);
        service = new OverdueLoanQueryService(loanRepository, readerNames, copySummaries, CLOCK);
        given(readerNames.findNames(anySet())).willAnswer(invocation -> {
            Set<Long> ids = invocation.getArgument(0);
            return ids.stream().collect(java.util.stream.Collectors.toMap(id -> id, id -> "Reader " + id));
        });
        given(copySummaries.findSummaries(anySet())).willAnswer(invocation -> {
            Set<Long> ids = invocation.getArgument(0);
            return ids.stream().collect(java.util.stream.Collectors.toMap(id -> id,
                    id -> new CopySummary(id, "PAT-" + id, 100L + id, "Book " + id)));
        });
    }

    /** An active loan picked up {@code daysAgo} days ago for 14 days: late by {@code daysAgo - 14} days. */
    private Loan openLoan(long copy, long reader, int daysAgo) {
        return loanRepository.save(Loan.create(copy, reader, TODAY.minusDays(daysAgo), 14));
    }

    private static ListOverdueLoansQuery firstPage() {
        return ListOverdueLoansQuery.of(0, 20);
    }

    @Test
    void shouldListTheLateLoansFromTheMostToTheLeastLate() {
        var least = openLoan(1L, 10L, 16);   // 2 days late
        var most = openLoan(2L, 11L, 40);    // 26 days late
        var middle = openLoan(3L, 12L, 25);  // 11 days late

        var page = service.list(firstPage());

        assertThat(page.items()).extracting(item -> item.loan().getId())
                .containsExactly(most.getId(), middle.getId(), least.getId());
        assertThat(page.items()).extracting(OverdueLoan::daysLate).containsExactly(26L, 11L, 2L);
    }

    @Test
    void aLoanDueTodayOrLaterShouldNotAppear() {
        openLoan(1L, 10L, 14);  // due today: still on time
        openLoan(2L, 11L, 5);   // due in 9 days
        var late = openLoan(3L, 12L, 15); // 1 day late

        var page = service.list(firstPage());

        assertThat(page.items()).extracting(item -> item.loan().getId()).containsExactly(late.getId());
        assertThat(page.totalItems()).isEqualTo(1);
    }

    @Test
    void aLoanAlreadyReturnedShouldNotAppearEvenIfItWasLate() {
        var returned = openLoan(1L, 10L, 40);
        returned.returnCopy(TODAY.minusDays(5));
        loanRepository.save(returned);
        var stillLate = openLoan(2L, 11L, 20);

        var page = service.list(firstPage());

        assertThat(page.items()).extracting(item -> item.loan().getId()).containsExactly(stillLate.getId());
    }

    @Test
    void eachItemShouldCarryTheReaderTheCopyAndTheBook() {
        openLoan(5L, 20L, 30);

        var item = service.list(firstPage()).items().get(0);

        assertThat(item.loan().getReaderId()).isEqualTo(20L);
        assertThat(item.readerName()).isEqualTo("Reader 20");
        assertThat(item.copy().copyId()).isEqualTo(5L);
        assertThat(item.copy().assetCode()).isEqualTo("PAT-5");
        assertThat(item.copy().bookTitle()).isEqualTo("Book 5");
    }

    @Test
    void theReadersAndCopiesOfAWholePageShouldBeAskedForInOneCallEach() {
        openLoan(1L, 10L, 30);
        openLoan(2L, 10L, 31);
        openLoan(3L, 11L, 32);

        service.list(firstPage());

        then(readerNames).should(org.mockito.Mockito.times(1)).findNames(Set.of(10L, 11L));
        then(copySummaries).should(org.mockito.Mockito.times(1)).findSummaries(Set.of(1L, 2L, 3L));
    }

    @Test
    void shouldPaginateKeepingTheOrderAcrossPages() {
        for (var days = 0; days < 5; days++) {
            openLoan(1L + days, 10L + days, 20 + days); // 6..10 days late
        }

        var first = service.list(ListOverdueLoansQuery.of(0, 2));
        var second = service.list(ListOverdueLoansQuery.of(1, 2));
        var third = service.list(ListOverdueLoansQuery.of(2, 2));

        assertThat(first.items()).extracting(OverdueLoan::daysLate).containsExactly(10L, 9L);
        assertThat(second.items()).extracting(OverdueLoan::daysLate).containsExactly(8L, 7L);
        assertThat(third.items()).extracting(OverdueLoan::daysLate).containsExactly(6L);
        assertThat(first.totalItems()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);
    }

    @Test
    void loansLateByTheSameDaysShouldComeInAStableOrder() {
        var a = openLoan(1L, 10L, 20);
        var b = openLoan(2L, 11L, 20);

        var page = service.list(firstPage());

        assertThat(page.items()).extracting(item -> item.loan().getId()).containsExactly(a.getId(), b.getId());
    }

    @Test
    void shouldCountTheDaysByTheDayOfTheClockInTheLibraryTimezone() {
        // 01:00 UTC of the 11th is still the 10th in São Paulo
        var lateNight = Clock.fixed(Instant.parse("2026-11-11T01:00:00Z"), ZONE);
        var lateService = new OverdueLoanQueryService(loanRepository, readerNames, copySummaries, lateNight);
        openLoan(1L, 10L, 20); // 6 days late on the 10th

        assertThat(lateService.list(firstPage()).items()).extracting(OverdueLoan::daysLate).containsExactly(6L);
    }

    @Test
    void aMissingReaderOrCopyShouldNotBreakTheReport() {
        openLoan(1L, 10L, 30);
        given(readerNames.findNames(anySet())).willReturn(Map.of());
        given(copySummaries.findSummaries(anySet())).willReturn(Map.of());

        var item = service.list(firstPage()).items().get(0);

        assertThat(item.readerName()).isNull();
        assertThat(item.copy()).isNull();
        assertThat(item.loan().getReaderId()).isEqualTo(10L);
        assertThat(item.loan().getCopyId()).isEqualTo(1L);
        assertThat(item.daysLate()).isEqualTo(16L);
    }

    @Test
    void anEmptyReportShouldBeAnEmptyPage() {
        openLoan(1L, 10L, 5);

        var page = service.list(firstPage());

        assertThat(page.items()).isEmpty();
        assertThat(page.totalItems()).isZero();
    }

    @Test
    void thePageSizeShouldNeverPassTheMaximum() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ListOverdueLoansQuery.of(0, 101))
                .isInstanceOf(InvalidQueryException.class);
        assertThat(ListOverdueLoansQuery.of(0, 100).page().size()).isEqualTo(100);
    }
}
