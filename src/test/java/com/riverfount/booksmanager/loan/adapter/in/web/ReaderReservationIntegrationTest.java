package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
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
 * The reservations of the reader (Phase 6) through the real path: security chain, transactions and PostgreSQL, with
 * no test transaction (it would hide the commit and the rollback). The point is the owner of the data: with two
 * readers, nobody reaches, cancels or learns anything about the reservation of the other.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ReaderReservationIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    private long bookId;
    private long copyId;
    private long ana;
    private long bia;
    private long carla;
    private int sequence;

    @BeforeEach
    void createTheBookAndThreeReaders() {
        cleanUp();
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('RRES test') returning id", Long.class);
        bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000007148', 'RRES Dom Casmurro', ?) returning id",
                Long.class, categoryId);
        // every copy of the book is out, so the book can be reserved
        copyId = jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, 'RRES-1', 'BORROWED') returning id",
                Long.class, bookId);
        ana = newReader("Ana");
        bia = newReader("Bia");
        carla = newReader("Carla");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from reservation where reader_id in (select id from reader where document like 'RRES-DOC-%')");
        jdbcTemplate.update("delete from copy where asset_code like 'RRES-%'");
        jdbcTemplate.update("delete from reader where document like 'RRES-DOC-%'");
        jdbcTemplate.update("delete from book where isbn = '9780000007148'");
        jdbcTemplate.update("delete from category where name = 'RRES test'");
    }

    private long newReader(String name) {
        return jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values (?, ?, 'r@example.com', date '1990-01-01') returning id
                """, Long.class, name, "RRES-DOC-" + ++sequence);
    }

    private String asReader(long readerId) {
        return "Bearer " + TestTokens.forReader(encoder, properties, 1000 + readerId, readerId);
    }

    private ResultActions reserve(long readerId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/me/reservations").header("Authorization", asReader(readerId))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions reserve(long readerId) throws Exception {
        return reserve(readerId, "{\"bookId\":" + bookId + "}");
    }

    private ResultActions cancel(long readerId, long reservationId) throws Exception {
        return mockMvc.perform(post("/api/v1/me/reservations/" + reservationId + "/cancel")
                .header("Authorization", asReader(readerId)));
    }

    private ResultActions find(long readerId, long reservationId) throws Exception {
        return mockMvc.perform(get("/api/v1/me/reservations/" + reservationId)
                .header("Authorization", asReader(readerId)));
    }

    private long waiting(long readerId, int daysAgo) {
        return jdbcTemplate.queryForObject("""
                insert into reservation (book_id, reader_id, reservation_date, status) values (?, ?, ?, 'ACTIVE')
                returning id
                """, Long.class, bookId, readerId, TODAY.minusDays(daysAgo));
    }

    /** The reservation that keeps the (reserved) copy of the book. */
    private long holdingTheCopy(long readerId) {
        jdbcTemplate.update("update copy set status = 'RESERVED' where id = ?", copyId);
        return jdbcTemplate.queryForObject("""
                insert into reservation (book_id, reader_id, reservation_date, status, copy_id, pickup_deadline)
                values (?, ?, ?, 'ACTIVE', ?, ?) returning id
                """, Long.class, bookId, readerId, TODAY.minusDays(5), copyId, TODAY.plusDays(2));
    }

    private String statusOf(long reservationId) {
        return jdbcTemplate.queryForObject("select status from reservation where id = ?", String.class, reservationId);
    }

    private long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    // reserving

    @Test
    void shouldReserveForTheReaderOfTheTokenWhateverTheBodySays() throws Exception {
        reserve(ana, "{\"bookId\":" + bookId + ",\"readerId\":" + bia + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookTitle").value("RRES Dom Casmurro"))
                .andExpect(jsonPath("$.queuePosition").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(count("select count(*) from reservation where reader_id = ?", ana)).isEqualTo(1);
        assertThat(count("select count(*) from reservation where reader_id = ?", bia)).isZero();
    }

    @Test
    void theSecondReaderShouldTakeTheNextPlaceInTheQueue() throws Exception {
        reserve(ana).andExpect(status().isCreated()).andExpect(jsonPath("$.queuePosition").value(1));
        reserve(bia).andExpect(status().isCreated()).andExpect(jsonPath("$.queuePosition").value(2));
    }

    @Test
    void aRefusedReservationShouldLeaveNothingBehind() throws Exception {
        jdbcTemplate.update("update copy set status = 'AVAILABLE' where id = ?", copyId);

        reserve(ana).andExpect(status().isUnprocessableContent());

        assertThat(count("select count(*) from reservation where book_id = ?", bookId)).isZero();
    }

    @Test
    void aReaderCannotReserveTheSameBookTwice() throws Exception {
        reserve(ana).andExpect(status().isCreated());

        reserve(ana).andExpect(status().isUnprocessableContent());

        assertThat(count("select count(*) from reservation where reader_id = ?", ana)).isEqualTo(1);
    }

    @Test
    void aBlockedReaderIsRefusedByTheRulesOfTheDesk() throws Exception {
        jdbcTemplate.update("update reader set blocked = true where id = ?", ana);

        reserve(ana).andExpect(status().isUnprocessableContent());

        assertThat(count("select count(*) from reservation where reader_id = ?", ana)).isZero();
    }

    @Test
    void twoSimultaneousReservationsOfTheSameBookShouldCreateOneOnly() throws Exception {
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> attempt = () -> {
                barrier.await();
                return reserve(ana).andReturn().getResponse().getStatus();
            };
            var results = pool.invokeAll(java.util.List.of(attempt, attempt));
            var statuses = new ArrayList<Integer>();
            for (var result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
            assertThat(statuses).filteredOn(code -> code != 201).allMatch(code -> code == 409 || code == 422);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("select count(*) from reservation where reader_id = ?", ana)).isEqualTo(1);
    }

    // looking: only what is theirs

    @Test
    void shouldListOnlyTheReservationsOfTheReader() throws Exception {
        var anas = waiting(ana, 3);
        waiting(bia, 2);

        mockMvc.perform(get("/api/v1/me/reservations").header("Authorization", asReader(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(anas))
                .andExpect(jsonPath("$.items[0].queuePosition").value(1));
        // a readerId in the query is not a filter of the reader: it is ignored
        mockMvc.perform(get("/api/v1/me/reservations?readerId=" + bia).header("Authorization", asReader(ana)))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(anas));
    }

    @Test
    void aReaderShouldGetThePlaceInTheQueueOfTheirOwnReservationOnly() throws Exception {
        waiting(ana, 3);
        var bias = waiting(bia, 2);

        find(bia, bias).andExpect(status().isOk()).andExpect(jsonPath("$.queuePosition").value(2));
    }

    @Test
    void theReservationOfAnotherReaderShouldBeA404ExactlyLikeOneThatDoesNotExist() throws Exception {
        var anas = waiting(ana, 3);

        var another = find(bia, anas).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        var missing = find(bia, anas + 100000).andExpect(status().isNotFound()).andReturn().getResponse()
                .getContentAsString();

        assertThat(another.replaceAll("\\d+", "N")).isEqualTo(missing.replaceAll("\\d+", "N"));
    }

    // cancelling

    @Test
    void shouldCancelTheirOwnReservationAndAnswerOnlyWithIt() throws Exception {
        var anas = waiting(ana, 3);
        waiting(bia, 2);

        cancel(ana, anas)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(anas))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.served").doesNotExist());

        assertThat(statusOf(anas)).isEqualTo("CANCELLED");
    }

    @Test
    void cancellingTheOneThatHoldsTheCopyShouldPassItOnWithoutTellingWhoGotIt() throws Exception {
        var anas = holdingTheCopy(ana);
        var carlas = waiting(carla, 1);

        cancel(ana, anas)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.pickupDeadline").doesNotExist())
                .andExpect(content().string(not(containsString("served"))))
                .andExpect(content().string(not(containsString("RRES-1"))))
                .andExpect(content().string(not(containsString("\"id\":" + carlas))));

        // the copy went to the next of the queue
        var next = jdbcTemplate.queryForMap("select copy_id, pickup_deadline from reservation where id = ?", carlas);
        assertThat(next.get("copy_id")).isEqualTo(copyId);
        assertThat(next.get("pickup_deadline")).isNotNull();
    }

    @Test
    void shouldNeverCancelTheReservationOfAnotherReader() throws Exception {
        var anas = waiting(ana, 3);

        cancel(bia, anas).andExpect(status().isNotFound());

        assertThat(statusOf(anas)).isEqualTo("ACTIVE");
    }

    @Test
    void theClosedReservationOfAnotherReaderIsA404NotA422() throws Exception {
        var anas = waiting(ana, 3);
        cancel(ana, anas).andExpect(status().isOk());

        // "already cancelled" would tell Bia the state of Ana's reservation
        cancel(bia, anas).andExpect(status().isNotFound());
        find(bia, anas).andExpect(status().isNotFound());
    }

    @Test
    void cancellingAClosedReservationOfTheirsIsA422AndAnUnknownOneA404() throws Exception {
        var anas = waiting(ana, 3);
        cancel(ana, anas).andExpect(status().isOk());

        cancel(ana, anas).andExpect(status().isUnprocessableContent());
        cancel(ana, anas + 100000).andExpect(status().isNotFound());
    }

    @Test
    void twoSimultaneousCancellationsOfTheSameReservationShouldCancelItOnce() throws Exception {
        var anas = holdingTheCopy(ana);
        var carlas = waiting(carla, 1);
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> attempt = () -> {
                barrier.await();
                return cancel(ana, anas).andReturn().getResponse().getStatus();
            };
            var results = pool.invokeAll(java.util.List.of(attempt, attempt));
            var statuses = new ArrayList<Integer>();
            for (var result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).filteredOn(code -> code == 200).hasSize(1);
            assertThat(statuses).filteredOn(code -> code != 200).allMatch(code -> code == 409 || code == 422);
        } finally {
            pool.shutdownNow();
        }
        assertThat(statusOf(anas)).isEqualTo("CANCELLED");
        assertThat(count("select count(*) from reservation where copy_id = ? and status = 'ACTIVE'", copyId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select copy_id from reservation where id = ?", Long.class, carlas))
                .isEqualTo(copyId);
    }

    // who may call

    @Test
    void theAdministrativeProfilesMayNotUseTheReservationsOfTheReader() throws Exception {
        var anas = waiting(ana, 3);
        for (var role : new String[] {"ADMIN", "OPERATOR"}) {
            var token = "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", role,
                    java.time.Instant.now(), java.time.Instant.now().plusSeconds(900));
            mockMvc.perform(get("/api/v1/me/reservations").header("Authorization", token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/me/reservations/" + anas + "/cancel").header("Authorization", token))
                    .andExpect(status().isForbidden());
        }
        assertThat(statusOf(anas)).isEqualTo("ACTIVE");
    }

    @Test
    void theReaderMayNotUseTheAdministrativeReservationRoutes() throws Exception {
        var anas = waiting(ana, 3);

        mockMvc.perform(get("/api/v1/reservations").header("Authorization", asReader(ana)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/reservations/" + anas + "/cancel").header("Authorization", asReader(ana)))
                .andExpect(status().isForbidden());
        assertThat(statusOf(anas)).isEqualTo("ACTIVE");
    }
}
