package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.riverfount.booksmanager.catalog.application.port.in.CopySummary;
import com.riverfount.booksmanager.catalog.application.port.in.FindCopySummariesUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ListMostBorrowedBooksQuery;
import com.riverfount.booksmanager.loan.application.port.in.MostBorrowedBook;
import com.riverfount.booksmanager.loan.domain.Loan;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The most borrowed books report (RF24): loans counted per copy, copies taken to their books through the
 * catalog's port, summed per book and ordered from the most borrowed, ties broken by title and then by id.
 */
class MostBorrowedBooksQueryServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);

    private InMemoryLoanRepository loanRepository;
    private FindCopySummariesUseCase copySummaries;
    private final Map<Long, CopySummary> catalog = new HashMap<>();
    private MostBorrowedBooksQueryService service;

    @BeforeEach
    void setUp() {
        loanRepository = new InMemoryLoanRepository();
        copySummaries = mock(FindCopySummariesUseCase.class);
        given(copySummaries.findSummaries(anySet())).willAnswer(invocation -> {
            var asked = invocation.<java.util.Set<Long>>getArgument(0);
            var found = new HashMap<Long, CopySummary>();
            asked.forEach(id -> {
                if (catalog.containsKey(id)) {
                    found.put(id, catalog.get(id));
                }
            });
            return found;
        });
        service = new MostBorrowedBooksQueryService(loanRepository, copySummaries);
    }

    /** The copy exists in the catalog, as a copy of the book with this title. */
    private void copy(long copyId, long bookId, String title) {
        catalog.put(copyId, new CopySummary(copyId, "PAT-" + copyId, bookId, title));
    }

    private long readers;

    /** A loan picked up on the day and returned the same day, so the copy can be lent again. */
    private void loan(long copyId, LocalDate pickup) {
        var loan = activeLoan(copyId, pickup);
        loan.returnCopy(pickup);
        loanRepository.save(loan);
    }

    /** A loan picked up on the day and still open. */
    private Loan activeLoan(long copyId, LocalDate pickup) {
        return loanRepository.save(Loan.create(copyId, 20L + readers++, pickup, 14));
    }

    private ListMostBorrowedBooksQuery query() {
        return ListMostBorrowedBooksQuery.of(FROM, TO, 0, 20);
    }

    private ListMostBorrowedBooksQuery page(int page, int size) {
        return ListMostBorrowedBooksQuery.of(FROM, TO, page, size);
    }

    @Test
    void shouldListTheBooksFromTheMostToTheLeastBorrowed() {
        copy(1L, 10L, "Dom Casmurro");
        copy(2L, 20L, "Quincas Borba");
        copy(3L, 30L, "Iracema");
        loan(2L, FROM);
        loan(1L, FROM.plusDays(5));
        loan(1L, FROM.plusDays(6));
        loan(1L, FROM.plusDays(7));
        loan(2L, FROM.plusDays(8));
        loan(3L, FROM.plusDays(9));

        var result = service.list(query());

        assertThat(result.items()).extracting(MostBorrowedBook::bookId, MostBorrowedBook::totalLoans)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(10L, 3L),
                        org.assertj.core.groups.Tuple.tuple(20L, 2L), org.assertj.core.groups.Tuple.tuple(30L, 1L));
        assertThat(result.items().get(0).title()).isEqualTo("Dom Casmurro");
        assertThat(result.totalItems()).isEqualTo(3);
    }

    @Test
    void shouldSumTheLoansOfAllTheCopiesOfTheSameBook() {
        copy(1L, 10L, "Dom Casmurro");
        copy(2L, 10L, "Dom Casmurro");
        copy(3L, 20L, "Iracema");
        loan(1L, FROM);
        loan(2L, FROM);
        loan(2L, FROM.plusDays(3));
        loan(3L, FROM);
        loan(3L, FROM.plusDays(1));

        var result = service.list(query());

        assertThat(result.items()).extracting(MostBorrowedBook::bookId, MostBorrowedBook::totalLoans)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(10L, 3L),
                        org.assertj.core.groups.Tuple.tuple(20L, 2L));
    }

    @Test
    void bothEndsOfThePeriodShouldBeInclusiveAndWhatIsOutsideShouldNotCount() {
        copy(1L, 10L, "Dom Casmurro");
        loan(1L, FROM.minusDays(1));
        loan(1L, FROM);
        loan(1L, TO);
        loan(1L, TO.plusDays(1));

        assertThat(service.list(query()).items()).singleElement()
                .extracting(MostBorrowedBook::totalLoans).isEqualTo(2L);
    }

    @Test
    void aRenewalShouldNotCountAsANewLoan() {
        copy(1L, 10L, "Dom Casmurro");
        var loan = activeLoan(1L, FROM);
        loan.renew(FROM.plusDays(3), 2, 14, false);
        loanRepository.save(loan);

        assertThat(service.list(query()).items()).singleElement()
                .extracting(MostBorrowedBook::totalLoans).isEqualTo(1L);
    }

    @Test
    void aReturnedAndAnActiveLoanShouldBothCount() {
        copy(1L, 10L, "Dom Casmurro");
        loan(1L, FROM);
        activeLoan(1L, FROM.plusDays(6));

        assertThat(service.list(query()).items()).singleElement()
                .extracting(MostBorrowedBook::totalLoans).isEqualTo(2L);
    }

    @Test
    void aTieShouldBeBrokenByTheTitleAndThenByTheId() {
        copy(1L, 30L, "Quincas Borba");
        copy(2L, 20L, "iracema");
        copy(3L, 10L, "Dom Casmurro");
        copy(4L, 40L, "Dom Casmurro");
        loan(1L, FROM);
        loan(2L, FROM);
        loan(3L, FROM);
        loan(4L, FROM);

        var result = service.list(query());

        assertThat(result.items()).extracting(MostBorrowedBook::bookId).containsExactly(10L, 40L, 20L, 30L);
    }

    @Test
    void shouldPaginateAfterOrderingAndCountTheWholeReport() {
        for (var book = 1L; book <= 5L; book++) {
            copy(book, book * 10, "Livro " + book);
            for (var n = 0; n < book; n++) {
                loan(book, FROM.plusDays(n));
            }
        }

        var second = service.list(page(1, 2));

        assertThat(second.items()).extracting(MostBorrowedBook::bookId).containsExactly(30L, 20L);
        assertThat(second.page()).isEqualTo(1);
        assertThat(second.size()).isEqualTo(2);
        assertThat(second.totalItems()).isEqualTo(5);
        assertThat(service.list(page(2, 2)).items()).extracting(MostBorrowedBook::bookId).containsExactly(10L);
        assertThat(service.list(page(9, 2)).items()).isEmpty();
    }

    @Test
    void shouldAnswerAnEmptyReportWhenNobodyBorrowedInThePeriod() {
        copy(1L, 10L, "Dom Casmurro");
        loan(1L, FROM.minusDays(10));

        var result = service.list(query());

        assertThat(result.items()).isEmpty();
        assertThat(result.totalItems()).isZero();
    }

    @Test
    void shouldSkipACopyTheCatalogNoLongerKnows() {
        copy(1L, 10L, "Dom Casmurro");
        loan(1L, FROM);
        loan(99L, FROM);

        assertThat(service.list(query()).items()).extracting(MostBorrowedBook::bookId).containsExactly(10L);
    }

    @Test
    void shouldAskTheCatalogOnceForAllTheCopiesNoMatterHowManyThereAre() {
        for (var copy = 1L; copy <= 6L; copy++) {
            copy(copy, copy % 3, "Livro " + copy % 3);
            loan(copy, FROM);
        }

        service.list(query());

        then(copySummaries).should(times(1)).findSummaries(anySet());
    }

    @Test
    void shouldNotAskTheCatalogWhenThereAreNoLoans() {
        service.list(query());

        // an empty set is still one call, but it must not fail and the report must be empty
        then(copySummaries).should(times(1)).findSummaries(anySet());
        then(copySummaries).should(never()).findSummaries(java.util.Set.of(1L));
    }
}
