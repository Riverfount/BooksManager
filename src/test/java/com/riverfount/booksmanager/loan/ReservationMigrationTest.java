package com.riverfount.booksmanager.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import java.util.List;
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
 * Verifies the schema created by migration V10__create_reservation.sql (RF16, RNF03, RNF17): the
 * reservation table, with constraints in the database that back the rules of the domain, above all
 * "one active reservation per reader and book" and "one active reservation per held copy".
 *
 * <p>Each rejection runs as its own test: in PostgreSQL a failed statement aborts the transaction
 * of the test.
 */
@Import(TestcontainersConfiguration.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class ReservationMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long bookId;
    private long otherBookId;
    private long copyId;
    private long otherCopyId;
    private long readerId;
    private long otherReaderId;

    @BeforeEach
    void createParents() {
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Reservation migration test') returning id", Long.class);
        bookId = newBook("9780000000101", categoryId);
        otherBookId = newBook("9780000000102", categoryId);
        copyId = newCopy(bookId, "RM-1");
        otherCopyId = newCopy(bookId, "RM-2");
        readerId = newReader("RM-DOC-1");
        otherReaderId = newReader("RM-DOC-2");
    }

    // shape

    @Test
    void shouldHaveTheMigrationRegisteredSuccessfully() {
        var success = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where script = 'V10__create_reservation.sql'",
                Boolean.class);

        assertThat(success).isTrue();
    }

    @Test
    void shouldHaveTheColumnsOfTheReservation() {
        var columns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_name = 'reservation'",
                String.class);

        assertThat(columns).containsExactlyInAnyOrder("id", "book_id", "reader_id", "copy_id", "pickup_deadline",
                "status", "reservation_date", "version");
    }

    // one active reservation per reader and book (RN12)

    @Test
    void shouldRefuseASecondActiveReservationOfTheSameBookByTheSameReader() {
        insert(bookId, readerId, null, null, "ACTIVE");

        assertThatThrownBy(() -> insert(bookId, readerId, null, null, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldAllowTheSameBookForAnotherReader() {
        insert(bookId, readerId, null, null, "ACTIVE");

        assertThatCode(() -> insert(bookId, otherReaderId, null, null, "ACTIVE")).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowAnotherBookForTheSameReader() {
        insert(bookId, readerId, null, null, "ACTIVE");

        assertThatCode(() -> insert(otherBookId, readerId, null, null, "ACTIVE")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELLED", "EXPIRED", "FULFILLED"})
    void shouldAllowANewReservationOnceThePreviousOneIsClosed(String closedStatus) {
        insert(bookId, readerId, copyId, "2026-11-04", closedStatus);

        assertThatCode(() -> insert(bookId, readerId, null, null, "ACTIVE")).doesNotThrowAnyException();
    }

    @Test
    void shouldKeepTheHistoryOfSeveralClosedReservationsOfTheSameReaderAndBook() {
        insert(bookId, readerId, null, null, "CANCELLED");
        insert(bookId, readerId, copyId, "2026-11-04", "EXPIRED");

        assertThatCode(() -> insert(bookId, readerId, copyId, "2026-11-06", "FULFILLED")).doesNotThrowAnyException();
    }

    // one active reservation per held copy

    @Test
    void shouldRefuseTwoActiveReservationsHoldingTheSameCopy() {
        insert(bookId, readerId, copyId, "2026-11-04", "ACTIVE");

        assertThatThrownBy(() -> insert(bookId, otherReaderId, copyId, "2026-11-04", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldAllowActiveReservationsHoldingDifferentCopies() {
        insert(bookId, readerId, copyId, "2026-11-04", "ACTIVE");

        assertThatCode(() -> insert(bookId, otherReaderId, otherCopyId, "2026-11-04", "ACTIVE"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowAnotherReservationToHoldTheCopyOnceThePreviousOneIsClosed() {
        insert(bookId, readerId, copyId, "2026-11-04", "EXPIRED");

        assertThatCode(() -> insert(bookId, otherReaderId, copyId, "2026-11-08", "ACTIVE"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowManyActiveReservationsWaitingWithoutACopy() {
        insert(bookId, readerId, null, null, "ACTIVE");

        assertThatCode(() -> insert(bookId, otherReaderId, null, null, "ACTIVE")).doesNotThrowAnyException();
    }

    // the copy and the deadline go together; a taken or expired reservation had a copy

    @Test
    void shouldRefuseACopyWithoutADeadline() {
        assertThatThrownBy(() -> insert(bookId, readerId, copyId, null, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRefuseADeadlineWithoutACopy() {
        assertThatThrownBy(() -> insert(bookId, readerId, null, "2026-11-04", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FULFILLED", "EXPIRED"})
    void shouldRefuseAFulfilledOrExpiredReservationWithoutACopy(String status) {
        assertThatThrownBy(() -> insert(bookId, readerId, null, null, status))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldAllowACancelledReservationThatNeverHeldACopy() {
        assertThatCode(() -> insert(bookId, readerId, null, null, "CANCELLED")).doesNotThrowAnyException();
    }

    @Test
    void shouldRefuseADeadlineBeforeTheReservationDate() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into reservation (book_id, reader_id, copy_id, pickup_deadline, status, reservation_date)
                values (?, ?, ?, date '2026-10-31', 'ACTIVE', date '2026-11-01')
                """, bookId, readerId, copyId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRefuseAnUnknownStatus() {
        assertThatThrownBy(() -> insert(bookId, readerId, null, null, "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // references

    @Test
    void shouldRefuseAnUnknownBook() {
        assertThatThrownBy(() -> insert(-1L, readerId, null, null, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRefuseAnUnknownReader() {
        assertThatThrownBy(() -> insert(bookId, -1L, null, null, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRefuseAnUnknownCopy() {
        assertThatThrownBy(() -> insert(bookId, readerId, -1L, "2026-11-04", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // indexes for the queue and the queries by reader

    @Test
    void shouldHaveTheIndexesOfTheQueueAndOfTheReaderQueries() {
        List<String> indexes = jdbcTemplate.queryForList(
                "select indexname from pg_indexes where tablename = 'reservation'", String.class);

        assertThat(indexes).contains("uq_reservation_active_reader_book", "uq_reservation_active_copy",
                "idx_reservation_book_queue", "idx_reservation_reader_id", "idx_reservation_active_deadline");
    }

    private void insert(long book, long reader, Long copy, String deadline, String status) {
        jdbcTemplate.update("""
                insert into reservation (book_id, reader_id, copy_id, pickup_deadline, status, reservation_date)
                values (?, ?, ?, cast(? as date), ?, date '2026-11-01')
                """, book, reader, copy, deadline, status);
    }

    private long newBook(String isbn, long categoryId) {
        return jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values (?, 'T', ?) returning id", Long.class, isbn,
                categoryId);
    }

    private long newCopy(long book, String assetCode) {
        return jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, ?, 'AVAILABLE') returning id",
                Long.class, book, assetCode);
    }

    private long newReader(String document) {
        return jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', ?, 'r@example.com', date '1990-01-01') returning id
                """, Long.class, document);
    }
}
