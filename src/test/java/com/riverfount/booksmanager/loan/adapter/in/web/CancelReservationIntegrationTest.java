package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Cancelling a reservation (RF17, RF18) through the real path: security chain, transaction and PostgreSQL, with
 * no test transaction (it would hide the commit and the rollback). The data is committed, so it is removed after
 * each test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class CancelReservationIntegrationTest {

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
    private int sequence;

    @BeforeEach
    void createParents() {
        cleanUp();
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Cancel reservation test') returning id", Long.class);
        bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000400', 'T', ?) returning id",
                Long.class, categoryId);
        copyId = jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, 'CRV-1', 'RESERVED') returning id",
                Long.class, bookId);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from audit_log where action = 'RESERVATION_CANCELLED'");
        jdbcTemplate.update("delete from reservation where reader_id in (select id from reader where document like 'CRV-DOC-%')");
        jdbcTemplate.update("delete from copy where asset_code like 'CRV-%'");
        jdbcTemplate.update("delete from reader where document like 'CRV-DOC-%'");
        jdbcTemplate.update("delete from book where isbn = '9780000000400'");
        jdbcTemplate.update("delete from category where name = 'Cancel reservation test'");
    }

    private long newReader() {
        return jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', ?, 'r@example.com', date '1990-01-01') returning id
                """, Long.class, "CRV-DOC-" + ++sequence);
    }

    /** A reservation waiting in the queue, made {@code daysAgo} days ago. */
    private long waiting(int daysAgo) {
        return jdbcTemplate.queryForObject("""
                insert into reservation (book_id, reader_id, reservation_date, status) values (?, ?, ?, 'ACTIVE')
                returning id
                """, Long.class, bookId, newReader(), TODAY.minusDays(daysAgo));
    }

    /** The reservation that keeps the (reserved) copy of the book. */
    private long holdingTheCopy() {
        return jdbcTemplate.queryForObject("""
                insert into reservation (book_id, reader_id, reservation_date, status, copy_id, pickup_deadline)
                values (?, ?, ?, 'ACTIVE', ?, ?) returning id
                """, Long.class, bookId, newReader(), TODAY.minusDays(5), copyId, TODAY.plusDays(2));
    }

    private ResultActions cancel(long reservationId) throws Exception {
        var token = "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", "OPERATOR", Instant.now(),
                Instant.now().plusSeconds(900));
        return mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/cancel")
                .header("Authorization", token));
    }

    private Map<String, Object> row(long reservationId) {
        return jdbcTemplate.queryForMap("select status, copy_id, pickup_deadline from reservation where id = ?",
                reservationId);
    }

    private String copyStatus() {
        return jdbcTemplate.queryForObject("select status from copy where id = ?", String.class, copyId);
    }

    @Test
    void shouldCancelAReservationThatWaitsInTheQueue() throws Exception {
        var first = waiting(3);
        var second = waiting(2);

        cancel(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservation.status").value("CANCELLED"))
                .andExpect(jsonPath("$.served").doesNotExist());

        assertThat(row(first).get("status")).isEqualTo("CANCELLED");
        assertThat(row(second).get("status")).isEqualTo("ACTIVE");
        assertThat(copyStatus()).isEqualTo("RESERVED"); // not touched: it was not kept for the cancelled one
    }

    @Test
    void cancellingTheReservationThatHoldsTheCopyShouldPassItToTheNextOfTheQueue() throws Exception {
        var holding = holdingTheCopy();
        var next = waiting(3);
        var afterNext = waiting(2);

        cancel(holding)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservation.status").value("CANCELLED"))
                .andExpect(jsonPath("$.served.id").value(next))
                .andExpect(jsonPath("$.served.copyId").value(copyId))
                .andExpect(jsonPath("$.served.pickupDeadline").value(TODAY.plusDays(3).toString()));

        assertThat(copyStatus()).isEqualTo("RESERVED");
        assertThat(row(next).get("copy_id")).isEqualTo(copyId);
        assertThat(row(next).get("pickup_deadline").toString()).isEqualTo(TODAY.plusDays(3).toString());
        assertThat(row(afterNext).get("copy_id")).isNull();
        assertThat(row(holding).get("status")).isEqualTo("CANCELLED");
    }

    @Test
    void cancellingTheReservationThatHoldsTheCopyWithNobodyWaitingShouldPutTheCopyBackOnTheShelf() throws Exception {
        var holding = holdingTheCopy();

        cancel(holding).andExpect(status().isOk()).andExpect(jsonPath("$.served").doesNotExist());

        assertThat(copyStatus()).isEqualTo("AVAILABLE");
        assertThat(row(holding).get("status")).isEqualTo("CANCELLED");
    }

    @Test
    void aReservationAlreadyClosedShouldAnswer422() throws Exception {
        var id = waiting(3);
        jdbcTemplate.update("update reservation set status = 'CANCELLED' where id = ?", id);

        cancel(id).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("já foi encerrada")));
    }

    @Test
    void anUnknownReservationShouldAnswer404() throws Exception {
        cancel(-1L).andExpect(status().isNotFound());
    }

    @Test
    void withoutATokenTheAnswerShouldBe401() throws Exception {
        mockMvc.perform(post("/api/v1/reservations/1/cancel")).andExpect(status().isUnauthorized());
    }

    @Test
    void aFailureWhilePassingTheCopyOnShouldUndoTheWholeCancellation() throws Exception {
        // the next of the queue is dated in the future, so the domain refuses to give it the copy, after the
        // reservation was cancelled and the copy released: everything must roll back
        var holding = holdingTheCopy();
        var next = jdbcTemplate.queryForObject("""
                insert into reservation (book_id, reader_id, reservation_date, status) values (?, ?, ?, 'ACTIVE')
                returning id
                """, Long.class, bookId, newReader(), TODAY.plusDays(1));

        cancel(holding).andExpect(status().isUnprocessableContent());

        assertThat(row(holding).get("status")).isEqualTo("ACTIVE");
        assertThat(row(holding).get("copy_id")).isEqualTo(copyId);
        assertThat(copyStatus()).isEqualTo("RESERVED");
        assertThat(row(next).get("copy_id")).isNull();
    }

    @Test
    void twoSimultaneousCancellationsShouldLetOnlyOnePass() throws Exception {
        var holding = holdingTheCopy();
        var next = waiting(3);
        var startTogether = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        var results = new ArrayList<Future<Integer>>();
        try {
            for (var i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    startTogether.await(10, TimeUnit.SECONDS);
                    return cancel(holding).andReturn().getResponse().getStatus();
                }));
            }
            var statuses = new ArrayList<Integer>();
            for (var result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses.stream().filter(s -> s == 200).count()).as("statuses %s", statuses).isEqualTo(1);
            assertThat(statuses).allMatch(s -> s == 200 || s == 409 || s == 422);
        } finally {
            pool.shutdownNow();
        }

        assertThat(row(holding).get("status")).isEqualTo("CANCELLED");
        assertThat(row(next).get("copy_id")).isEqualTo(copyId);
        assertThat(copyStatus()).isEqualTo("RESERVED");
    }

    private java.util.List<Map<String, Object>> cancellationEntries() {
        return jdbcTemplate.queryForList("""
                select actor_id, action, target_type, target_id, summary from audit_log
                where action = 'RESERVATION_CANCELLED' order by id
                """);
    }

    @Test
    void cancellingShouldLeaveOneAuditEntryWithTheCallerAndNoPersonalDataNorAssetCode() throws Exception {
        var holding = holdingTheCopy();

        cancel(holding).andExpect(status().isOk());

        assertThat(cancellationEntries()).singleElement().satisfies(entry -> {
            assertThat(entry.get("actor_id")).isEqualTo(1L);
            assertThat(entry.get("target_type")).isEqualTo("RESERVATION");
            assertThat(entry.get("target_id")).isEqualTo(holding);
            assertThat(entry.get("summary")).isEqualTo("livro " + bookId + ", com exemplar reservado");
        });
    }

    @Test
    void aRefusedCancellationShouldLeaveNoAuditEntry() throws Exception {
        var id = waiting(3);
        cancel(id).andExpect(status().isOk());

        cancel(id).andExpect(status().isUnprocessableContent());
        cancel(-1L).andExpect(status().isNotFound());

        assertThat(cancellationEntries()).hasSize(1);
    }

    @Test
    void aCancellationUndoneByAFailureShouldLeaveNoAuditEntry() throws Exception {
        var holding = holdingTheCopy();
        jdbcTemplate.queryForObject("""
                insert into reservation (book_id, reader_id, reservation_date, status) values (?, ?, ?, 'ACTIVE')
                returning id
                """, Long.class, bookId, newReader(), TODAY.plusDays(1));

        cancel(holding).andExpect(status().isUnprocessableContent());

        assertThat(cancellationEntries()).isEmpty();
    }
}
