package com.riverfount.booksmanager.loan.journey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.loan.application.port.in.ExpireReservationsUseCase;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The whole life of the reservations (RF16 to RF19, RN03, RN07, RN13) through the real path, end to end: the
 * security chain, the transactions and PostgreSQL, with no test transaction (it would hide the commit, the
 * rollback and the races), across the catalog, the user and the loan modules. The pieces have their own tests;
 * this one walks the journey and then races the operations that can meet, and after each scenario checks that
 * no copy is left in an incoherent state: {@code RESERVED} without a reservation that holds it, or a reservation
 * that holds a copy that is not {@code RESERVED}, or one of another book.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ReservationJourneyIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    private static final int RACE_ROUNDS = 6;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    @Autowired
    private ExpireReservationsUseCase expireReservations;

    private long categoryId;
    private int sequence;

    @BeforeEach
    void createParents() {
        cleanUp();
        categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Reservation journey') returning id", Long.class);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from reservation where reader_id in (select id from reader where document like 'JRN-DOC-%')");
        jdbcTemplate.update("delete from loan where reader_id in (select id from reader where document like 'JRN-DOC-%')");
        jdbcTemplate.update("delete from copy where asset_code like 'JRN-%'");
        jdbcTemplate.update("delete from reader where document like 'JRN-DOC-%'");
        jdbcTemplate.update("delete from book where isbn like '978000007%'");
        jdbcTemplate.update("delete from category where name = 'Reservation journey'");
    }

    // fixtures (straight into the database) and operations (through the API)

    private long newBook() {
        return jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values (?, 'T', ?) returning id", Long.class,
                isbn("978000007" + String.format("%03d", ++sequence)), categoryId);
    }

    /** The 12 digits plus the ISBN-13 check digit: the catalog refuses a book whose ISBN does not check out. */
    private static String isbn(String twelveDigits) {
        var sum = 0;
        for (var i = 0; i < 12; i++) {
            sum += (twelveDigits.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return twelveDigits + (10 - sum % 10) % 10;
    }

    private long newCopy(long book) {
        return jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, ?, 'AVAILABLE') returning id", Long.class,
                book, "JRN-" + ++sequence);
    }

    private long newReader() {
        return jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', ?, 'r@example.com', date '1990-01-01') returning id
                """, Long.class, "JRN-DOC-" + ++sequence);
    }

    private ResultActions send(String path, String json) throws Exception {
        var token = "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", "OPERATOR", Instant.now(),
                Instant.now().plusSeconds(900));
        var request = post(path).header("Authorization", token);
        return mockMvc.perform(json == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions lend(long copy, long reader) throws Exception {
        return send("/api/v1/loans", "{\"copyId\":" + copy + ",\"readerId\":" + reader + "}");
    }

    private ResultActions reserve(long book, long reader) throws Exception {
        return send("/api/v1/reservations", "{\"bookId\":" + book + ",\"readerId\":" + reader + "}");
    }

    private ResultActions giveBack(long loan) throws Exception {
        return send("/api/v1/loans/" + loan + "/return", null);
    }

    private ResultActions renew(long loan) throws Exception {
        return send("/api/v1/loans/" + loan + "/renew", null);
    }

    private ResultActions cancel(long reservation) throws Exception {
        return send("/api/v1/reservations/" + reservation + "/cancel", null);
    }

    private static long idOf(ResultActions result) throws Exception {
        var response = result.andReturn().getResponse();
        assertThat(response.getStatus()).as("the response was %s", response.getContentAsString()).isBetween(200, 299);
        return ((Number) JsonPath.read(response.getContentAsString(), "$.id")).longValue();
    }

    private static int statusOf(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getStatus();
    }

    private String copyStatus(long copy) {
        return jdbcTemplate.queryForObject("select status from copy where id = ?", String.class, copy);
    }

    private Map<String, Object> reservation(long id) {
        return jdbcTemplate.queryForMap("select status, copy_id, pickup_deadline, reader_id from reservation where id = ?",
                id);
    }

    /** Pretends the reservation's deadline ended yesterday, as if the days had gone by. */
    private void letTheDeadlineEnd(long reservationId) {
        jdbcTemplate.update("update reservation set reservation_date = ?, pickup_deadline = ? where id = ?",
                TODAY.minusDays(10), TODAY.minusDays(1), reservationId);
    }

    /** No copy may be left in an incoherent state, whatever raced (RNF17). */
    private void assertNothingIsIncoherent() {
        assertThat(jdbcTemplate.queryForList("""
                select c.id from copy c where c.asset_code like 'JRN-%' and c.status = 'RESERVED'
                  and not exists (select 1 from reservation r where r.copy_id = c.id and r.status = 'ACTIVE')
                """, Long.class)).as("copies RESERVED with no active reservation holding them").isEmpty();
        assertThat(jdbcTemplate.queryForList("""
                select r.id from reservation r join copy c on c.id = r.copy_id
                where c.asset_code like 'JRN-%' and r.status = 'ACTIVE' and c.status <> 'RESERVED'
                """, Long.class)).as("active reservations holding a copy that is not RESERVED").isEmpty();
        assertThat(jdbcTemplate.queryForList("""
                select r.id from reservation r join copy c on c.id = r.copy_id
                where c.asset_code like 'JRN-%' and r.book_id <> c.book_id
                """, Long.class)).as("reservations holding a copy of another book").isEmpty();
        assertThat(jdbcTemplate.queryForList("""
                select l.id from loan l join copy c on c.id = l.copy_id
                where c.asset_code like 'JRN-%' and l.return_date is null and c.status <> 'BORROWED'
                """, Long.class)).as("open loans on a copy that is not BORROWED").isEmpty();
        assertThat(jdbcTemplate.queryForList("""
                select copy_id from reservation where copy_id is not null and status = 'ACTIVE'
                group by copy_id having count(*) > 1
                """, Long.class)).as("a copy held by two active reservations").isEmpty();
    }

    // the journey

    @Test
    void theWholeJourneyOfAReservationShouldWorkThroughTheRealPath() throws Exception {
        var book = newBook();
        var copy = newCopy(book);
        var reader1 = newReader();
        var reader2 = newReader();
        var reader3 = newReader();
        var reader4 = newReader();
        var reader5 = newReader();
        var reader6 = newReader();

        // the only copy goes out on loan
        var loan1 = idOf(lend(copy, reader1).andExpect(status().isCreated()));
        assertThat(copyStatus(copy)).isEqualTo("BORROWED");

        // two readers reserve the book: the queue follows the order of arrival
        var reservation2 = idOf(reserve(book, reader2).andExpect(status().isCreated())
                .andExpect(jsonPath("$.queuePosition").value(1)));
        var reservation3 = idOf(reserve(book, reader3).andExpect(status().isCreated())
                .andExpect(jsonPath("$.queuePosition").value(2)));

        // the borrower cannot renew while somebody waits for the book (RN07)
        renew(loan1).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("reserva esperando na fila")));

        // the return hands the copy to the first of the queue (RF18)
        giveBack(loan1).andExpect(status().isOk())
                .andExpect(jsonPath("$.reservation.id").value(reservation2))
                .andExpect(jsonPath("$.reservation.readerId").value(reader2))
                .andExpect(jsonPath("$.reservation.pickupDeadline").value(TODAY.plusDays(3).toString()));
        assertThat(copyStatus(copy)).isEqualTo("RESERVED");
        assertNothingIsIncoherent();

        // the second of the queue cannot take the copy kept for the first (RN03)
        lend(copy, reader3).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("exemplar reservado para outro leitor")));

        // the first picks it up: the reservation is fulfilled and the second moves up
        var loan2 = idOf(lend(copy, reader2).andExpect(status().isCreated()));
        assertThat(copyStatus(copy)).isEqualTo("BORROWED");
        assertThat(reservation(reservation2).get("status")).isEqualTo("FULFILLED");
        assertThat(reservation(reservation3).get("copy_id")).isNull();

        // the next return hands the copy to the second
        giveBack(loan2).andExpect(status().isOk()).andExpect(jsonPath("$.reservation.id").value(reservation3));
        assertThat(reservation(reservation3).get("copy_id")).isEqualTo(copy);
        assertThat(copyStatus(copy)).isEqualTo("RESERVED");

        // the second does not come: the deadline ends and the routine passes the turn to the next reader (RF19)
        var reservation4 = idOf(reserve(book, reader4).andExpect(status().isCreated())
                .andExpect(jsonPath("$.queuePosition").value(1)));
        letTheDeadlineEnd(reservation3);
        assertThat(expireReservations.expireOverdue()).isEqualTo(1);
        assertThat(reservation(reservation3).get("status")).isEqualTo("EXPIRED");
        assertThat(reservation(reservation4).get("copy_id")).isEqualTo(copy);
        assertThat(copyStatus(copy)).isEqualTo("RESERVED");
        assertNothingIsIncoherent();

        // the fourth is cancelled by the operator and the copy goes to the fifth (RF17)
        var reservation5 = idOf(reserve(book, reader5).andExpect(status().isCreated()));
        cancel(reservation4).andExpect(status().isOk()).andExpect(jsonPath("$.served.id").value(reservation5));
        assertThat(reservation(reservation5).get("copy_id")).isEqualTo(copy);
        assertThat(copyStatus(copy)).isEqualTo("RESERVED");

        // the fifth picks it up; while the sixth waits the loan cannot be renewed, and once that one gives up it can
        var loan5 = idOf(lend(copy, reader5).andExpect(status().isCreated()));
        var reservation6 = idOf(reserve(book, reader6).andExpect(status().isCreated()));
        renew(loan5).andExpect(status().isUnprocessableContent());
        cancel(reservation6).andExpect(status().isOk());
        renew(loan5).andExpect(status().isOk()).andExpect(jsonPath("$.renewals").value(1));

        // with nobody waiting, the return leaves the copy on the shelf
        giveBack(loan5).andExpect(status().isOk()).andExpect(jsonPath("$.reservation").doesNotExist());
        assertThat(copyStatus(copy)).isEqualTo("AVAILABLE");
        assertNothingIsIncoherent();
    }

    @Test
    void aCopyThatComesBackDamagedShouldNeverGoToTheQueue() throws Exception {
        var book = newBook();
        var copy = newCopy(book);
        var borrower = newReader();
        var waiting = newReader();
        var loan = idOf(lend(copy, borrower).andExpect(status().isCreated()));
        var reservation = idOf(reserve(book, waiting).andExpect(status().isCreated()));

        send("/api/v1/loans/" + loan + "/return", "{\"condition\":\"DAMAGED\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.reservation").doesNotExist());

        assertThat(copyStatus(copy)).isEqualTo("DAMAGED");
        assertThat(reservation(reservation).get("copy_id")).isNull();
        assertNothingIsIncoherent();
    }

    // the races

    /** Runs the tasks at the same moment, each on its own thread, and returns what each one gave. */
    private static <T> List<T> together(List<Callable<T>> tasks) throws Exception {
        var startTogether = new CyclicBarrier(tasks.size());
        var pool = Executors.newFixedThreadPool(tasks.size());
        try {
            var futures = new ArrayList<Future<T>>();
            for (var task : tasks) {
                futures.add(pool.submit(() -> {
                    startTogether.await(10, TimeUnit.SECONDS);
                    return task.call();
                }));
            }
            var results = new ArrayList<T>();
            for (var future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void readersReservingTheSameBookAtTheSameTimeShouldAllGetADistinctPlaceInTheQueue() throws Exception {
        for (var round = 0; round < RACE_ROUNDS; round++) {
            var book = newBook();
            var copy = newCopy(book);
            lend(copy, newReader()).andExpect(status().isCreated());
            var readers = List.of(newReader(), newReader(), newReader());

            var statuses = together(readers.stream()
                    .<Callable<Integer>>map(reader -> () -> statusOf(reserve(book, reader))).toList());

            assertThat(statuses).as("round %d", round).containsOnly(201);
            var places = jdbcTemplate.queryForList("select id from reservation where book_id = ? order by id",
                    Long.class, book);
            assertThat(places).hasSize(3);
            for (var i = 0; i < places.size(); i++) {
                var position = ((Number) JsonPath.read(
                        mockMvcGet("/api/v1/reservations/" + places.get(i)), "$.queuePosition")).intValue();
                assertThat(position).as("the queue follows the order of the ids").isEqualTo(i + 1);
            }
            assertNothingIsIncoherent();
        }
    }

    private String mockMvcGet(String path) throws Exception {
        var token = "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", "OPERATOR", Instant.now(),
                Instant.now().plusSeconds(900));
        return mockMvc.perform(get(path)
                .header("Authorization", token)).andReturn().getResponse().getContentAsString();
    }

    @Test
    void aReturnAndTheCancellationOfTheFirstOfTheQueueAtTheSameTimeShouldLeaveEverythingCoherent() throws Exception {
        for (var round = 0; round < RACE_ROUNDS; round++) {
            var book = newBook();
            var copy = newCopy(book);
            var loan = idOf(lend(copy, newReader()).andExpect(status().isCreated()));
            var first = idOf(reserve(book, newReader()).andExpect(status().isCreated()));
            var second = idOf(reserve(book, newReader()).andExpect(status().isCreated()));

            var statuses = together(List.<Callable<Integer>>of(
                    () -> statusOf(giveBack(loan)), () -> statusOf(cancel(first))));

            assertThat(statuses).as("round %d: return, cancel", round).allMatch(s -> s == 200 || s == 409 || s == 422);
            assertNothingIsIncoherent();
            // the first reservation is closed or holds the copy; never both in the queue and holding
            var firstRow = reservation(first);
            assertThat(firstRow.get("status")).isIn("ACTIVE", "CANCELLED");
            if (firstRow.get("status").equals("CANCELLED")) {
                assertThat(statuses.get(1)).isEqualTo(200);
            }
            // whoever holds the copy, if anybody, is a reservation of this book; the second never jumps the first
            var holders = jdbcTemplate.queryForList(
                    "select id from reservation where book_id = ? and status = 'ACTIVE' and copy_id is not null",
                    Long.class, book);
            assertThat(holders).hasSizeLessThanOrEqualTo(1);
            if (!holders.isEmpty() && "ACTIVE".equals(firstRow.get("status"))) {
                assertThat(holders).containsExactly(first);
            }
            assertThat(second).isNotNull();
        }
    }

    @Test
    void theTwoReturnsOfTheSameBookAtTheSameTimeShouldGiveOneCopyToTheOneThatWaits() throws Exception {
        for (var round = 0; round < RACE_ROUNDS; round++) {
            var book = newBook();
            var copyA = newCopy(book);
            var copyB = newCopy(book);
            var loanA = idOf(lend(copyA, newReader()).andExpect(status().isCreated()));
            var loanB = idOf(lend(copyB, newReader()).andExpect(status().isCreated()));
            var waiting = idOf(reserve(book, newReader()).andExpect(status().isCreated()));

            var statuses = together(List.<Callable<Integer>>of(
                    () -> statusOf(giveBack(loanA)), () -> statusOf(giveBack(loanB))));

            assertThat(statuses).as("round %d", round).allMatch(s -> s == 200 || s == 409);
            assertNothingIsIncoherent();
            var held = jdbcTemplate.queryForList(
                    "select copy_id from reservation where id = ? and copy_id is not null", Long.class, waiting);
            assertThat(held).hasSizeLessThanOrEqualTo(1);
            // each copy is either still out, kept for the reader who waits, or back on the shelf
            for (var copy : List.of(copyA, copyB)) {
                assertThat(copyStatus(copy)).isIn("BORROWED", "RESERVED", "AVAILABLE");
            }
            // the reader who waits ends with a copy as long as at least one return went through
            if (statuses.contains(200)) {
                assertThat(reservation(waiting).get("copy_id")).isNotNull();
            }
        }
    }

    @Test
    void theExpirationAndAPickUpAfterTheDeadlineAtTheSameTimeShouldNeverBothWin() throws Exception {
        for (var round = 0; round < RACE_ROUNDS; round++) {
            var book = newBook();
            var copy = newCopy(book);
            var owner = newReader();
            var loan = idOf(lend(copy, newReader()).andExpect(status().isCreated()));
            var reservationId = idOf(reserve(book, owner).andExpect(status().isCreated()));
            giveBack(loan).andExpect(status().isOk());
            letTheDeadlineEnd(reservationId);

            var results = together(List.<Callable<Integer>>of(
                    () -> statusOf(lend(copy, owner)), () -> expireReservations.expireOverdue()));

            assertThat(results.get(0)).as("round %d: the date refuses the owner after the deadline", round).isEqualTo(422);
            assertThat(results.get(1)).isEqualTo(1);
            assertThat(reservation(reservationId).get("status")).isEqualTo("EXPIRED");
            assertThat(copyStatus(copy)).isEqualTo("AVAILABLE");
            assertNothingIsIncoherent();
        }
    }

    @Test
    void thePickUpOnTheLastDayAndTheExpirationRoutineAtTheSameTimeShouldLetThePickUpWin() throws Exception {
        for (var round = 0; round < RACE_ROUNDS; round++) {
            var book = newBook();
            var copy = newCopy(book);
            var owner = newReader();
            var loan = idOf(lend(copy, newReader()).andExpect(status().isCreated()));
            var reservationId = idOf(reserve(book, owner).andExpect(status().isCreated()));
            giveBack(loan).andExpect(status().isOk());
            jdbcTemplate.update("update reservation set reservation_date = ?, pickup_deadline = ? where id = ?",
                    TODAY.minusDays(3), TODAY, reservationId); // today is the last day

            var results = together(List.<Callable<Integer>>of(
                    () -> statusOf(lend(copy, owner)), () -> expireReservations.expireOverdue()));

            assertThat(results.get(0)).as("round %d", round).isEqualTo(201);
            assertThat(results.get(1)).isZero();
            assertThat(reservation(reservationId).get("status")).isEqualTo("FULFILLED");
            assertThat(copyStatus(copy)).isEqualTo("BORROWED");
            assertNothingIsIncoherent();
        }
    }

    @Test
    void theExpirationAndTheCancellationOfTheSameReservationAtTheSameTimeShouldCloseItOnce() throws Exception {
        for (var round = 0; round < RACE_ROUNDS; round++) {
            var book = newBook();
            var copy = newCopy(book);
            var loan = idOf(lend(copy, newReader()).andExpect(status().isCreated()));
            var reservationId = idOf(reserve(book, newReader()).andExpect(status().isCreated()));
            var next = idOf(reserve(book, newReader()).andExpect(status().isCreated()));
            giveBack(loan).andExpect(status().isOk());
            letTheDeadlineEnd(reservationId);

            var results = together(List.<Callable<Integer>>of(
                    () -> statusOf(cancel(reservationId)), () -> expireReservations.expireOverdue()));

            assertThat(results.get(0)).as("round %d", round).isIn(200, 409, 422);
            assertThat(reservation(reservationId).get("status")).isIn("CANCELLED", "EXPIRED");
            // whichever won, the copy went on to the next of the queue exactly once
            assertThat(reservation(next).get("copy_id")).isEqualTo(copy);
            assertThat(copyStatus(copy)).isEqualTo("RESERVED");
            assertNothingIsIncoherent();
        }
    }
}
