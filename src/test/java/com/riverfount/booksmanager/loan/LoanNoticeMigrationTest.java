package com.riverfount.booksmanager.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.PersistenceTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies the schema created by migration V11__add_loan_notices.sql (RF28, RN16): the days of notice in the library
 * configuration and the table of the notices already sent, whose unique key is what stops the same e-mail from being
 * recorded (and so sent) twice. Each rejection runs as its own test: in PostgreSQL a failed statement aborts the
 * transaction of the test.
 */
@PersistenceTest
class LoanNoticeMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long loanId;

    @BeforeEach
    void createLoan() {
        var category = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Loan notice migration test') returning id", Long.class);
        var book = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000026', 'T', ?) returning id",
                Long.class, category);
        var copy = jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, 'LNM-1', 'AVAILABLE') returning id",
                Long.class, book);
        var reader = jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', 'LNM-DOC-1', 'r@example.com', date '1990-01-01') returning id
                """, Long.class);
        loanId = jdbcTemplate.queryForObject("""
                insert into loan (copy_id, reader_id, pickup_date, due_date)
                values (?, ?, date '2026-11-01', date '2026-11-15') returning id
                """, Long.class, copy, reader);
    }

    private void insertNotice(String kind, String dueDate, int band) {
        jdbcTemplate.update("""
                insert into loan_notice (loan_id, kind, due_date, band, sent_on)
                values (?, ?, cast(? as date), ?, date '2026-11-14')
                """, loanId, kind, dueDate, band);
    }

    @Test
    void shouldHaveTheMigrationRegisteredSuccessfully() {
        var success = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where script = 'V11__add_loan_notices.sql'",
                Boolean.class);

        assertThat(success).isTrue();
    }

    @Test
    void shouldHaveTheColumnsOfTheNoticeTable() {
        var columns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_name = 'loan_notice'", String.class);

        assertThat(columns).containsExactlyInAnyOrder("id", "loan_id", "kind", "due_date", "band", "sent_on");
    }

    @Test
    void theConfigurationShouldStartWithTwoDaysOfNotice() {
        var days = jdbcTemplate.queryForObject("select due_soon_notice_days from library_configuration", Integer.class);

        assertThat(days).isEqualTo(2);
    }

    @Test
    void theDaysOfNoticeShouldBeAtLeastOne() {
        assertThatThrownBy(() -> jdbcTemplate.update("update library_configuration set due_soon_notice_days = 0"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRecordEachKindOfNotice() {
        insertNotice("DUE_SOON", "2026-11-15", 0);
        insertNotice("OVERDUE", "2026-11-15", 0);
        insertNotice("OVERDUE", "2026-11-15", 1);

        List<Integer> bands = jdbcTemplate.queryForList("select band from loan_notice where loan_id = ? order by id",
                Integer.class, loanId);
        assertThat(bands).containsExactly(0, 0, 1);
    }

    @Test
    void shouldRefuseTheSameNoticeTwice() {
        insertNotice("DUE_SOON", "2026-11-15", 0);

        assertThatThrownBy(() -> insertNotice("DUE_SOON", "2026-11-15", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aNewDueDateShouldBeANewNotice() {
        insertNotice("DUE_SOON", "2026-11-15", 0);

        insertNotice("DUE_SOON", "2026-11-29", 0);

        assertThat(jdbcTemplate.queryForObject("select count(*) from loan_notice where loan_id = ?", Integer.class,
                loanId)).isEqualTo(2);
    }

    @Test
    void shouldRefuseAnUnknownKind() {
        assertThatThrownBy(() -> insertNotice("SOMETHING", "2026-11-15", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRefuseANegativeBand() {
        assertThatThrownBy(() -> insertNotice("OVERDUE", "2026-11-15", -1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRefuseANoticeOfALoanThatDoesNotExist() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into loan_notice (loan_id, kind, due_date, band, sent_on)
                values (-1, 'DUE_SOON', date '2026-11-15', 0, date '2026-11-14')
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
