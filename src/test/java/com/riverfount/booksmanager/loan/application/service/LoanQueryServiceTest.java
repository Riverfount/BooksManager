package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.catalog.application.port.in.CopySummary;
import com.riverfount.booksmanager.catalog.application.port.in.FindCopySummariesUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ListLoansQuery;
import com.riverfount.booksmanager.loan.application.port.in.LoanDetails;
import com.riverfount.booksmanager.loan.application.port.in.LoanStatus;
import com.riverfount.booksmanager.loan.domain.Fine;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.loan.domain.Suspension;
import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.user.application.port.in.FindReaderNamesUseCase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

/** Querying loans (RF14, HU30): active and closed, by reader and by copy, always paginated (RNF14). */
class LoanQueryServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 1);
    private static final long READER = 20L;
    private static final long OTHER_READER = 21L;

    private InMemoryLoanRepository loanRepository;
    private InMemoryFineRepository fineRepository;
    private InMemorySuspensionRepository suspensionRepository;
    private FindReaderNamesUseCase readerNames;
    private FindCopySummariesUseCase copySummaries;
    private LoanQueryService service;

    @BeforeEach
    void setUp() {
        loanRepository = new InMemoryLoanRepository();
        fineRepository = new InMemoryFineRepository();
        suspensionRepository = new InMemorySuspensionRepository();
        readerNames = mock(FindReaderNamesUseCase.class);
        copySummaries = mock(FindCopySummariesUseCase.class);
        service = new LoanQueryService(loanRepository, fineRepository, suspensionRepository, readerNames,
                copySummaries);
    }

    private Loan openLoan(long copy, long reader, LocalDate pickup) {
        return loanRepository.save(Loan.create(copy, reader, pickup, 14));
    }

    private Loan returnedLoan(long copy, long reader, LocalDate pickup, LocalDate returnedOn) {
        var loan = openLoan(copy, reader, pickup);
        loan.returnCopy(returnedOn);
        return loanRepository.save(loan);
    }

    private static ListLoansQuery query(Long reader, Long copy, LoanStatus status) {
        return new ListLoansQuery(reader, copy, status, PageQuery.of(0, 20, "id"));
    }

    // by id

    @Test
    void shouldFindALoanById() {
        var loan = openLoan(1L, READER, DAY);

        var details = service.findById(loan.getId()).orElseThrow();

        assertThat(details.loan().getId()).isEqualTo(loan.getId());
        assertThat(details.fine()).isNull();
        assertThat(details.suspension()).isNull();
    }

    @Test
    void shouldFindNothingForAnUnknownLoan() {
        assertThat(service.findById(999L)).isEmpty();
    }

    @Test
    void aClosedLoanShouldStillBeFoundWithItsPenalty() {
        var loan = returnedLoan(1L, READER, DAY, DAY.plusDays(20));
        var fine = fineRepository.save(Fine.create(loan.getId(), READER, new BigDecimal("6.00")));
        var suspension = suspensionRepository.save(Suspension.create(READER, loan.getId(), DAY.plusDays(20), 6));

        var details = service.findById(loan.getId()).orElseThrow();

        assertThat(details.loan().isReturned()).isTrue();
        assertThat(details.fine().getId()).isEqualTo(fine.getId());
        assertThat(details.suspension().getId()).isEqualTo(suspension.getId());
    }

    // listing

    @Test
    void shouldListTheLoansOfAReader() {
        var mine = openLoan(1L, READER, DAY);
        openLoan(2L, OTHER_READER, DAY);

        var page = service.list(query(READER, null, null));

        assertThat(page.items()).extracting(details -> details.loan().getId()).containsExactly(mine.getId());
    }

    @Test
    void shouldListTheLoansOfACopyIncludingTheClosedOnes() {
        var first = returnedLoan(1L, READER, DAY, DAY.plusDays(3));
        var second = openLoan(1L, OTHER_READER, DAY.plusDays(4));
        openLoan(2L, READER, DAY);

        var page = service.list(query(null, 1L, null));

        assertThat(page.items()).extracting(details -> details.loan().getId())
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void shouldSeparateActiveFromClosed() {
        var closed = returnedLoan(1L, READER, DAY, DAY.plusDays(3));
        var active = openLoan(2L, READER, DAY);

        assertThat(service.list(query(READER, null, LoanStatus.ACTIVE)).items())
                .extracting(details -> details.loan().getId()).containsExactly(active.getId());
        assertThat(service.list(query(READER, null, LoanStatus.RETURNED)).items())
                .extracting(details -> details.loan().getId()).containsExactly(closed.getId());
        assertThat(service.list(query(READER, null, null)).items()).hasSize(2);
    }

    @Test
    void theHistoryShouldCarryThePenaltyOfEachLoanAndNothingForTheOthers() {
        var late = returnedLoan(1L, READER, DAY, DAY.plusDays(20));
        var onTime = returnedLoan(2L, READER, DAY, DAY.plusDays(5));
        var fine = fineRepository.save(Fine.create(late.getId(), READER, new BigDecimal("6.00")));
        suspensionRepository.save(Suspension.create(READER, late.getId(), DAY.plusDays(20), 6));

        var page = service.list(query(READER, null, LoanStatus.RETURNED));

        var byLoan = page.items().stream().collect(java.util.stream.Collectors.toMap(
                details -> details.loan().getId(), details -> details));
        assertThat(byLoan.get(late.getId()).fine().getId()).isEqualTo(fine.getId());
        assertThat(byLoan.get(late.getId()).suspension()).isNotNull();
        assertThat(byLoan.get(onTime.getId()).fine()).isNull();
        assertThat(byLoan.get(onTime.getId()).suspension()).isNull();
    }

    @Test
    void shouldPaginateAndSort() {
        for (var day = 0; day < 3; day++) {
            openLoan(10L + day, READER, DAY.plusDays(day));
        }

        var page = service.list(new ListLoansQuery(READER, null, null, PageQuery.of(0, 2, "pickupDate,desc")));

        assertThat(page.items()).extracting(details -> details.loan().getPickupDate())
                .containsExactly(DAY.plusDays(2), DAY.plusDays(1));
        assertThat(page.totalItems()).isEqualTo(3);
        assertThat(page.size()).isEqualTo(2);
    }

    @Test
    void anEmptyResultShouldBeAnEmptyPage() {
        var page = service.list(query(999L, null, null));

        assertThat(page.items()).isEmpty();
        assertThat(page.totalItems()).isZero();
    }

    // names of the reader and of the copy (RF14)

    @Test
    void shouldFindALoanWithTheReaderNameAndTheCopySummary() {
        var loan = openLoan(7L, READER, DAY);
        var summary = new CopySummary(7L, "PAT-0007", 3L, "Dom Casmurro");
        given(readerNames.findNames(Set.of(READER))).willReturn(Map.of(READER, "Maria Silva"));
        given(copySummaries.findSummaries(Set.of(7L))).willReturn(Map.of(7L, summary));

        var details = service.findById(loan.getId()).orElseThrow();

        assertThat(details.readerName()).isEqualTo("Maria Silva");
        assertThat(details.copy()).isEqualTo(summary);
    }

    @Test
    void shouldListEachLoanWithItsOwnNamesFetchedOncePerPage() {
        openLoan(7L, READER, DAY);
        openLoan(8L, OTHER_READER, DAY);
        openLoan(9L, READER, DAY);
        given(readerNames.findNames(Set.of(READER, OTHER_READER)))
                .willReturn(Map.of(READER, "Maria Silva", OTHER_READER, "João Souza"));
        given(copySummaries.findSummaries(Set.of(7L, 8L, 9L))).willReturn(Map.of(
                7L, new CopySummary(7L, "PAT-0007", 3L, "Dom Casmurro"),
                8L, new CopySummary(8L, "PAT-0008", 4L, "Quincas Borba"),
                9L, new CopySummary(9L, "PAT-0009", 3L, "Dom Casmurro")));

        var page = service.list(query(null, null, null));

        assertThat(page.items()).extracting(LoanDetails::readerName)
                .containsExactly("Maria Silva", "João Souza", "Maria Silva");
        assertThat(page.items()).extracting(details -> details.copy().assetCode())
                .containsExactly("PAT-0007", "PAT-0008", "PAT-0009");
        then(readerNames).should(times(1)).findNames(Set.of(READER, OTHER_READER));
        then(copySummaries).should(times(1)).findSummaries(Set.of(7L, 8L, 9L));
    }

    @Test
    void anUnknownReaderOrCopyShouldLeaveTheNamesNullInsteadOfFailing() {
        var loan = openLoan(7L, READER, DAY);

        var details = service.findById(loan.getId()).orElseThrow();

        assertThat(details.readerName()).isNull();
        assertThat(details.copy()).isNull();
    }

    // the query (RNF14)

    @Test
    void shouldRefuseASortFieldThatIsNotSupported() {
        assertThatThrownBy(() -> new ListLoansQuery(null, null, null, PageQuery.of(0, 20, "readerId")))
                .isInstanceOf(InvalidQueryException.class).hasMessageContaining("readerId");
    }

    @Test
    void shouldAcceptEverySupportedSortField() {
        for (var field : ListLoansQuery.SORTABLE_FIELDS) {
            assertThat(new ListLoansQuery(null, null, null, PageQuery.of(0, 20, field))).isNotNull();
        }
    }

    @Test
    void theDefaultSortShouldBeValidAndShowTheMostRecentFirst() {
        var query = new ListLoansQuery(null, null, null, PageQuery.of(0, 20, ListLoansQuery.DEFAULT_SORT));

        assertThat(query.page().sortBy()).isEqualTo("pickupDate");
    }

    @Test
    void thePageSizeShouldNeverPassTheMaximum() {
        assertThatThrownBy(() -> PageQuery.of(0, 101, "id")).isInstanceOf(InvalidQueryException.class);
    }
}
