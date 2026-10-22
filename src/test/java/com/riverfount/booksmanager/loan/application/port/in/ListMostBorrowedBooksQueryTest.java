package com.riverfount.booksmanager.loan.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.SortDirection;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** The period and the page of the most borrowed books report (RF24, RNF14). */
class ListMostBorrowedBooksQueryTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);

    @Test
    void shouldAcceptAPeriodOfASingleDayAndOfExactlyTheLongestLength() {
        assertThatCode(() -> ListMostBorrowedBooksQuery.of(FROM, FROM, 0, 20)).doesNotThrowAnyException();
        // 2026-01-01 to 2027-01-01 are 366 days, both ends counted
        assertThatCode(() -> ListMostBorrowedBooksQuery.of(FROM, FROM.plusDays(365), 0, 20))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRefuseAPeriodLongerThanTheLimit() {
        assertThatThrownBy(() -> ListMostBorrowedBooksQuery.of(FROM, FROM.plusDays(366), 0, 20))
                .isInstanceOf(InvalidQueryException.class).hasMessageContaining("366 dias");
    }

    @Test
    void shouldRefuseAnInvertedPeriod() {
        assertThatThrownBy(() -> ListMostBorrowedBooksQuery.of(FROM, FROM.minusDays(1), 0, 20))
                .isInstanceOf(InvalidQueryException.class).hasMessageContaining("invertido");
    }

    @Test
    void shouldRefuseAMissingEndOfThePeriod() {
        assertThatThrownBy(() -> ListMostBorrowedBooksQuery.of(null, FROM, 0, 20))
                .isInstanceOf(InvalidQueryException.class).hasMessageContaining("obrigatório");
        assertThatThrownBy(() -> ListMostBorrowedBooksQuery.of(FROM, null, 0, 20))
                .isInstanceOf(InvalidQueryException.class).hasMessageContaining("obrigatório");
    }

    @Test
    void shouldRefuseAPageSizeAbove100() {
        assertThatThrownBy(() -> ListMostBorrowedBooksQuery.of(FROM, FROM, 0, 101))
                .isInstanceOf(InvalidQueryException.class);
    }

    @Test
    void theOrderShouldBeFixedFromTheMostBorrowed() {
        var query = ListMostBorrowedBooksQuery.of(FROM, FROM, 2, 10);

        assertThat(query.page().sortBy()).isEqualTo("totalLoans");
        assertThat(query.page().direction()).isEqualTo(SortDirection.DESC);
        assertThat(query.page().page()).isEqualTo(2);
    }
}
