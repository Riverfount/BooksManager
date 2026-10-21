package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.loan.application.port.in.ExpireReservationsUseCase;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Expiring the reservations past their deadline (RF19, RN13) through the real path: transactions and PostgreSQL,
 * with no test transaction (it would hide the commit and the rollback of each reservation). The data is
 * committed, so it is removed after each test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ExpireReservationsIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @Autowired
    private ExpireReservationsUseCase expireReservations;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    private long categoryId;
    private int sequence;

    @BeforeEach
    void createParents() {
        cleanUp();
        categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Expire reservations test') returning id", Long.class);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from reservation where reader_id in (select id from reader where document like 'EXP-DOC-%')");
        jdbcTemplate.update("delete from loan where reader_id in (select id from reader where document like 'EXP-DOC-%')");
        jdbcTemplate.update("delete from copy where asset_code like 'EXP-%'");
        jdbcTemplate.update("delete from reader where document like 'EXP-DOC-%'");
        jdbcTemplate.update("delete from book where isbn like '97800000005%'");
        jdbcTemplate.update("delete from category where name = 'Expire reservations test'");
    }

    private long newBook() {
        return jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values (?, 'T', ?) returning id", Long.class,
                String.format("97800000005%02d", ++sequence), categoryId);
    }

    private long newReader() {
        return jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', ?, 'r@example.com', date '1990-01-01') returning id
                """, Long.class, "EXP-DOC-" + ++sequence);
    }

    /** A reserved copy of the book. */
    private long reservedCopy(long book) {
        return jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, ?, 'RESERVED') returning id", Long.class,
                book, "EXP-" + ++sequence);
    }

    /** A reservation that holds the copy until {@code deadline} (inclusive). */
    private long holding(long book, long copy, LocalDate deadline) {
        return jdbcTemplate.queryForObject("""
                insert into reservation (book_id, reader_id, reservation_date, status, copy_id, pickup_deadline)
                values (?, ?, ?, 'ACTIVE', ?, ?) returning id
                """, Long.class, book, newReader(), deadline.minusDays(10), copy, deadline);
    }

    private long waiting(long book, int daysAgo) {
        return jdbcTemplate.queryForObject("""
                insert into reservation (book_id, reader_id, reservation_date, status) values (?, ?, ?, 'ACTIVE')
                returning id
                """, Long.class, book, newReader(), TODAY.minusDays(daysAgo));
    }

    private Map<String, Object> row(long reservationId) {
        return jdbcTemplate.queryForMap("select status, copy_id, pickup_deadline from reservation where id = ?",
                reservationId);
    }

    private String copyStatus(long copyId) {
        return jdbcTemplate.queryForObject("select status from copy where id = ?", String.class, copyId);
    }

    @Test
    void shouldExpireOnlyWhatIsPastTheDeadlineAndNotTheLastDay() throws Exception {
        var book = newBook();
        var copyBefore = reservedCopy(book);
        var copyLastDay = reservedCopy(book);
        var copyLate = reservedCopy(book);
        var before = holding(book, copyBefore, TODAY.plusDays(1));
        var lastDay = holding(book, copyLastDay, TODAY);
        var late = holding(book, copyLate, TODAY.minusDays(1));

        assertThat(expireReservations.expireOverdue()).isEqualTo(1);

        assertThat(row(before).get("status")).isEqualTo("ACTIVE");
        assertThat(row(lastDay).get("status")).isEqualTo("ACTIVE");
        assertThat(row(late).get("status")).isEqualTo("EXPIRED");
        assertThat(copyStatus(copyBefore)).isEqualTo("RESERVED");
        assertThat(copyStatus(copyLastDay)).isEqualTo("RESERVED");
        assertThat(copyStatus(copyLate)).isEqualTo("AVAILABLE"); // nobody waits for the book
    }

    @Test
    void shouldPassTheCopyToTheNextOfTheQueueStartingTheDeadlineToday() throws Exception {
        var book = newBook();
        var copy = reservedCopy(book);
        var late = holding(book, copy, TODAY.minusDays(1));
        var next = waiting(book, 5);
        var afterNext = waiting(book, 4);

        assertThat(expireReservations.expireOverdue()).isEqualTo(1);

        assertThat(row(late).get("status")).isEqualTo("EXPIRED");
        assertThat(copyStatus(copy)).isEqualTo("RESERVED");
        assertThat(row(next).get("copy_id")).isEqualTo(copy);
        assertThat(row(next).get("pickup_deadline").toString()).isEqualTo(TODAY.plusDays(3).toString());
        assertThat(row(afterNext).get("copy_id")).isNull();
    }

    @Test
    void runningTwiceInARowShouldChangeNothingTheSecondTime() throws Exception {
        var book = newBook();
        var copy = reservedCopy(book);
        holding(book, copy, TODAY.minusDays(1));
        var next = waiting(book, 5);

        assertThat(expireReservations.expireOverdue()).isEqualTo(1);
        var afterFirst = row(next);

        assertThat(expireReservations.expireOverdue()).isZero();
        assertThat(row(next)).isEqualTo(afterFirst);
        assertThat(copyStatus(copy)).isEqualTo("RESERVED");
    }

    @Test
    void severalExpiredReservationsOfTheSameBookShouldFeedTheQueueInOrder() throws Exception {
        var book = newBook();
        var copyA = reservedCopy(book);
        var copyB = reservedCopy(book);
        holding(book, copyA, TODAY.minusDays(2));
        holding(book, copyB, TODAY.minusDays(1));
        var third = waiting(book, 5);
        var fourth = waiting(book, 4);
        var fifth = waiting(book, 3);

        assertThat(expireReservations.expireOverdue()).isEqualTo(2);

        assertThat(row(third).get("copy_id")).isEqualTo(copyA);
        assertThat(row(fourth).get("copy_id")).isEqualTo(copyB);
        assertThat(row(fifth).get("copy_id")).isNull();
    }

    @Test
    void aReservationThatFailsShouldBeUndoneAndNotStopTheOthers() throws Exception {
        // the next of the queue of the first book is dated in the future, so the domain refuses to give it the
        // copy, after the reservation was expired and the copy released: that reservation alone must roll back
        var failingBook = newBook();
        var failingCopy = reservedCopy(failingBook);
        var failing = holding(failingBook, failingCopy, TODAY.minusDays(3)); // the oldest deadline: handled first
        jdbcTemplate.update("""
                insert into reservation (book_id, reader_id, reservation_date, status) values (?, ?, ?, 'ACTIVE')
                """, failingBook, newReader(), TODAY.plusDays(1));
        var fineBook = newBook();
        var fineCopy = reservedCopy(fineBook);
        var fine = holding(fineBook, fineCopy, TODAY.minusDays(1));

        assertThat(expireReservations.expireOverdue()).isEqualTo(1);

        assertThat(row(failing).get("status")).isEqualTo("ACTIVE");
        assertThat(row(failing).get("copy_id")).isEqualTo(failingCopy);
        assertThat(copyStatus(failingCopy)).isEqualTo("RESERVED");
        assertThat(row(fine).get("status")).isEqualTo("EXPIRED");
        assertThat(copyStatus(fineCopy)).isEqualTo("AVAILABLE");
    }

    @Test
    void twoRunsAtTheSameTimeShouldExpireEachReservationOnlyOnce() throws Exception {
        var book = newBook();
        var copy = reservedCopy(book);
        var late = holding(book, copy, TODAY.minusDays(1));
        var next = waiting(book, 5);
        var startTogether = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        var results = new ArrayList<Future<Integer>>();
        try {
            for (var i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    startTogether.await(10, TimeUnit.SECONDS);
                    return expireReservations.expireOverdue();
                }));
            }
            var total = 0;
            for (var result : results) {
                total += result.get(30, TimeUnit.SECONDS);
            }
            assertThat(total).as("reservations expired by the two runs together").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(row(late).get("status")).isEqualTo("EXPIRED");
        assertThat(row(next).get("copy_id")).isEqualTo(copy);
        assertThat(copyStatus(copy)).isEqualTo("RESERVED");
    }

    // the race with the pickup (RN13): the date decides, so the two never both win

    private ResultActions lend(long copy, long reader) throws Exception {
        var token = "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", "OPERATOR", Instant.now(),
                Instant.now().plusSeconds(900));
        return mockMvc.perform(post("/api/v1/loans").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"copyId\":" + copy + ",\"readerId\":" + reader + "}"));
    }

    private long readerOf(long reservationId) {
        return jdbcTemplate.queryForObject("select reader_id from reservation where id = ?", Long.class,
                reservationId);
    }

    @Test
    void onTheLastDayTheOwnerPicksUpAndTheRoutineLeavesItAlone() throws Exception {
        var book = newBook();
        var copy = reservedCopy(book);
        var reservation = holding(book, copy, TODAY);

        assertThat(expireReservations.expireOverdue()).isZero();
        lend(copy, readerOf(reservation)).andExpect(status().isCreated());

        assertThat(row(reservation).get("status")).isEqualTo("FULFILLED");
        assertThat(copyStatus(copy)).isEqualTo("BORROWED");
    }

    @Test
    void theDayAfterTheOwnerIsRefusedEvenBeforeTheRoutineRunsAndThenTheRoutineExpiresIt() throws Exception {
        var book = newBook();
        var copy = reservedCopy(book);
        var reservation = holding(book, copy, TODAY.minusDays(1));

        lend(copy, readerOf(reservation)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("prazo de retirada")));
        assertThat(row(reservation).get("status")).isEqualTo("ACTIVE");

        assertThat(expireReservations.expireOverdue()).isEqualTo(1);
        assertThat(row(reservation).get("status")).isEqualTo("EXPIRED");
        assertThat(copyStatus(copy)).isEqualTo("AVAILABLE");
    }
}
