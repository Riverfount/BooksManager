package com.riverfount.booksmanager.loan.adapter.out.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.catalog.application.port.out.ActiveLoanChecker;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The catalog's question "does this copy have an active loan?" (RN10) answered from the loan
 * module's data. Goes through the bean the catalog receives, so it also shows that the two
 * modules wire together with no cycle. Rolled back after each test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class ActiveLoanCheckerAdapterTest {

    @Autowired
    private ActiveLoanChecker checker;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long bookId;
    private long readerId;
    private int sequence;

    @BeforeEach
    void createParents() {
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Active loan checker') returning id", Long.class);
        bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000125', 'T', ?) returning id",
                Long.class, categoryId);
        readerId = jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', 'ALC-DOC-1', 'r@example.com', date '1990-01-01') returning id
                """, Long.class);
    }

    private long copy() {
        return jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, ?, 'AVAILABLE') returning id",
                Long.class, bookId, "ALC-" + ++sequence);
    }

    private void loan(long copyId, LocalDate returnedOn) {
        jdbcTemplate.update("""
                insert into loan (copy_id, reader_id, pickup_date, due_date, return_date, return_condition)
                values (?, ?, date '2026-01-01', date '2026-01-15', ?, case when ?::date is null then null else 'GOOD' end)
                """, copyId, readerId, returnedOn, returnedOn);
    }

    @Test
    void aCopyWithAnActiveLoanShouldBeReported() {
        var copyId = copy();
        loan(copyId, null);

        assertThat(checker.hasActiveLoan(copyId)).isTrue();
    }

    @Test
    void theLoanIsTheSourceOfTruthNotTheStatusOfTheCopy() {
        var copyId = copy(); // the status says AVAILABLE
        loan(copyId, null);

        assertThat(jdbcTemplate.queryForObject("select status from copy where id = ?", String.class, copyId))
                .isEqualTo("AVAILABLE");
        assertThat(checker.hasActiveLoan(copyId)).isTrue();
    }

    @Test
    void aCopyWhoseLoansWereAllReturnedShouldNotBeReported() {
        var copyId = copy();
        loan(copyId, LocalDate.of(2026, 1, 10));
        loan(copyId, LocalDate.of(2026, 2, 10));

        assertThat(checker.hasActiveLoan(copyId)).isFalse();
    }

    @Test
    void aCopyThatWasNeverLentShouldNotBeReported() {
        assertThat(checker.hasActiveLoan(copy())).isFalse();
    }

    @Test
    void onlyTheLoansOfThatCopyCount() {
        var lent = copy();
        var other = copy();
        loan(lent, null);

        assertThat(checker.hasActiveLoan(other)).isFalse();
        assertThat(checker.hasActiveLoan(lent)).isTrue();
    }

    @Test
    void aCopyThatDoesNotExistShouldNotBeReported() {
        assertThat(checker.hasActiveLoan(-1L)).isFalse();
    }
}
