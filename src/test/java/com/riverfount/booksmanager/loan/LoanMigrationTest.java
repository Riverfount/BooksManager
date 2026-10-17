package com.riverfount.booksmanager.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies the schema created by migration V7__create_loan.sql (RNF17, RNF03,
 * RN03): the loan, fine and suspension tables, with constraints in the database
 * that back the rules of the domain, above all "one active loan per copy".
 *
 * <p>Each rejection runs as its own test: in PostgreSQL a failed statement
 * aborts the transaction of the test.
 */
@Import(TestcontainersConfiguration.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class LoanMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long copyId;
    private long otherCopyId;
    private long readerId;

    @BeforeEach
    void createParents() {
        var categoryId = insertAndGetId("insert into category (name) values ('Loan migration test')");
        var bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000002', 'T', ?) returning id",
                Long.class, categoryId);
        copyId = jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, 'LM-1', 'AVAILABLE') returning id",
                Long.class, bookId);
        otherCopyId = jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, 'LM-2', 'AVAILABLE') returning id",
                Long.class, bookId);
        readerId = insertAndGetId("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', 'LM-DOC', 'r@example.com', date '1990-01-01')
                """);
    }

    // shape

    @Test
    void shouldHaveTheMigrationRegisteredSuccessfully() {
        var success = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where script = 'V7__create_loan.sql'", Boolean.class);

        assertThat(success).isTrue();
    }

    @Test
    void shouldHaveTheColumnsOfTheLoan() {
        assertThat(columnsOf("loan")).containsExactlyInAnyOrder("id", "copy_id", "reader_id", "pickup_date",
                "due_date", "return_date", "renewals", "version");
    }

    @Test
    void shouldHaveTheColumnsOfTheFine() {
        assertThat(columnsOf("fine")).containsExactlyInAnyOrder("id", "loan_id", "reader_id", "amount", "status",
                "waiver_reason", "version");
    }

    @Test
    void shouldHaveTheColumnsOfTheSuspension() {
        assertThat(columnsOf("suspension")).containsExactlyInAnyOrder("id", "reader_id", "loan_id", "start_date",
                "end_date", "ended_early_on", "ended_early_reason", "version");
    }

    @Test
    void shouldKeepTheFineAmountAsAFixedPointNumber() {
        assertThat(jdbcTemplate.queryForObject(
                "select data_type from information_schema.columns where table_name = 'fine' and column_name = 'amount'",
                String.class)).isEqualTo("numeric");
    }

    // one active loan per copy (RN03, RNF17)

    @Test
    void shouldRefuseASecondActiveLoanOfTheSameCopy() {
        insertLoan(copyId, "2026-11-01", "2026-11-15", null);

        assertThatThrownBy(() -> insertLoan(copyId, "2026-11-02", "2026-11-16", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldAllowActiveLoansOfDifferentCopies() {
        insertLoan(copyId, "2026-11-01", "2026-11-15", null);

        assertThatCode(() -> insertLoan(otherCopyId, "2026-11-01", "2026-11-15", null)).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowANewLoanOfACopyOnceThePreviousOneWasReturned() {
        insertLoan(copyId, "2026-11-01", "2026-11-15", "2026-11-10");

        assertThatCode(() -> insertLoan(copyId, "2026-11-11", "2026-11-25", null)).doesNotThrowAnyException();
    }

    @Test
    void shouldKeepTheHistoryOfSeveralReturnedLoansOfTheSameCopy() {
        insertLoan(copyId, "2026-10-01", "2026-10-15", "2026-10-10");

        assertThatCode(() -> insertLoan(copyId, "2026-11-01", "2026-11-15", "2026-11-05")).doesNotThrowAnyException();
    }

    // the loan

    @Test
    void shouldRefuseALoanOfAnUnknownCopy() {
        assertThatThrownBy(() -> insertLoan(-1L, "2026-11-01", "2026-11-15", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRefuseALoanOfAnUnknownReader() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into loan (copy_id, reader_id, pickup_date, due_date) values (?, -1, date '2026-11-01', "
                        + "date '2026-11-15')", copyId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRefuseADueDateBeforeThePickup() {
        assertThatThrownBy(() -> insertLoan(copyId, "2026-11-10", "2026-11-09", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRefuseAReturnBeforeThePickup() {
        assertThatThrownBy(() -> insertLoan(copyId, "2026-11-10", "2026-11-24", "2026-11-09"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRefuseNegativeRenewals() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into loan (copy_id, reader_id, pickup_date, due_date, renewals)
                values (?, ?, date '2026-11-01', date '2026-11-15', -1)
                """, copyId, readerId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldStartAnEmptyLoanWithNoRenewalsAndVersionZero() {
        var loanId = insertLoan(copyId, "2026-11-01", "2026-11-15", null);

        var row = jdbcTemplate.queryForMap("select renewals, version from loan where id = ?", loanId);

        assertThat(row.get("renewals")).isEqualTo(0);
        assertThat(row.get("version")).isEqualTo(0L);
    }

    @Test
    void shouldRequireTheDatesButNotTheReturn() {
        var nullable = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_name = 'loan' "
                        + "and is_nullable = 'YES'", String.class);

        assertThat(nullable).containsExactly("return_date");
    }

    // the fine: one per loan (RN05)

    @Test
    void shouldAcceptAFineForALoan() {
        var loanId = insertLoan(copyId, "2026-11-01", "2026-11-15", "2026-11-18");

        assertThatCode(() -> insertFine(loanId, "3.00", "PENDING", null)).doesNotThrowAnyException();
    }

    @Test
    void shouldRefuseASecondFineForTheSameLoan() {
        var loanId = insertLoan(copyId, "2026-11-01", "2026-11-15", "2026-11-18");
        insertFine(loanId, "3.00", "PENDING", null);

        assertThatThrownBy(() -> insertFine(loanId, "4.00", "PENDING", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRefuseAFineOfAnUnknownLoan() {
        assertThatThrownBy(() -> insertFine(-1L, "3.00", "PENDING", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-1.00"})
    void shouldRefuseAFineWithoutAPositiveAmount(String amount) {
        var loanId = insertLoan(copyId, "2026-11-01", "2026-11-15", "2026-11-18");

        assertThatThrownBy(() -> insertFine(loanId, amount, "PENDING", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRefuseAnUnknownFineStatus() {
        var loanId = insertLoan(copyId, "2026-11-01", "2026-11-15", "2026-11-18");

        assertThatThrownBy(() -> insertFine(loanId, "3.00", "pending", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRefuseAWaivedFineWithoutTheReason() {
        var loanId = insertLoan(copyId, "2026-11-01", "2026-11-15", "2026-11-18");

        assertThatThrownBy(() -> insertFine(loanId, "3.00", "WAIVED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRefuseAReasonOnAFineThatWasNotWaived() {
        var loanId = insertLoan(copyId, "2026-11-01", "2026-11-15", "2026-11-18");

        assertThatThrownBy(() -> insertFine(loanId, "3.00", "PAID", "no reason"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldAcceptAWaivedFineWithTheReason() {
        var loanId = insertLoan(copyId, "2026-11-01", "2026-11-15", "2026-11-18");

        assertThatCode(() -> insertFine(loanId, "3.00", "WAIVED", "Hospital stay")).doesNotThrowAnyException();
    }

    // the suspension (RN15)

    @Test
    void shouldAcceptASuspension() {
        var loanId = insertLoan(copyId, "2026-11-01", "2026-11-15", "2026-11-18");

        assertThatCode(() -> insertSuspension(loanId, "2026-11-18", "2026-11-21", null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRefuseASuspensionOfAnUnknownLoan() {
        assertThatThrownBy(() -> insertSuspension(-1L, "2026-11-18", "2026-11-21", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRefuseASuspensionThatEndsOnItsStartDay() {
        var loanId = insertLoan(copyId, "2026-11-01", "2026-11-15", "2026-11-18");

        assertThatThrownBy(() -> insertSuspension(loanId, "2026-11-18", "2026-11-18", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRefuseEndingEarlyWithoutTheReason() {
        var loanId = insertLoan(copyId, "2026-11-01", "2026-11-15", "2026-11-18");

        assertThatThrownBy(() -> insertSuspension(loanId, "2026-11-18", "2026-11-21", "2026-11-19", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRefuseAReasonWithoutEndingEarly() {
        var loanId = insertLoan(copyId, "2026-11-01", "2026-11-15", "2026-11-18");

        assertThatThrownBy(() -> insertSuspension(loanId, "2026-11-18", "2026-11-21", null, "why"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRefuseEndingEarlyAfterTheCalculatedEnd() {
        var loanId = insertLoan(copyId, "2026-11-01", "2026-11-15", "2026-11-18");

        assertThatThrownBy(() -> insertSuspension(loanId, "2026-11-18", "2026-11-21", "2026-11-22", "why"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldAcceptEndingEarlyWithTheReason() {
        var loanId = insertLoan(copyId, "2026-11-01", "2026-11-15", "2026-11-18");

        assertThatCode(() -> insertSuspension(loanId, "2026-11-18", "2026-11-21", "2026-11-19", "Pardoned"))
                .doesNotThrowAnyException();
    }

    // the queries of the use cases (reader, copy, overdue, pending fines, suspensions in force)

    @ParameterizedTest
    @ValueSource(strings = {"loan:reader_id", "loan:copy_id", "loan:due_date", "fine:reader_id",
            "suspension:reader_id", "suspension:loan_id"})
    void shouldIndexTheColumnsTheQueriesFilterBy(String tableAndColumn) {
        var parts = tableAndColumn.split(":");

        var indexed = jdbcTemplate.queryForObject("""
                select count(*) from pg_indexes
                where tablename = ? and indexdef ~ ('\\(' || ? || '[,)]')
                """, Integer.class, parts[0], parts[1]);

        assertThat(indexed).as("index on %s", tableAndColumn).isPositive();
    }

    private long insertAndGetId(String sql) {
        return jdbcTemplate.queryForObject(sql + " returning id", Long.class);
    }

    private java.util.List<String> columnsOf(String table) {
        return jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_name = ?", String.class, table);
    }

    private long insertLoan(long copy, String pickup, String due, String returned) {
        return jdbcTemplate.queryForObject("""
                insert into loan (copy_id, reader_id, pickup_date, due_date, return_date)
                values (?, ?, ?::date, ?::date, ?::date) returning id
                """, Long.class, copy, readerId, pickup, due, returned);
    }

    private void insertFine(long loanId, String amount, String status, String waiverReason) {
        jdbcTemplate.update("""
                insert into fine (loan_id, reader_id, amount, status, waiver_reason)
                values (?, ?, ?::numeric, ?, ?)
                """, loanId, readerId, amount, status, waiverReason);
    }

    private void insertSuspension(long loanId, String start, String end, String endedEarlyOn, String reason) {
        jdbcTemplate.update("""
                insert into suspension (reader_id, loan_id, start_date, end_date, ended_early_on, ended_early_reason)
                values (?, ?, ?::date, ?::date, ?::date, ?)
                """, readerId, loanId, start, end, endedEarlyOn, reason);
    }
}
