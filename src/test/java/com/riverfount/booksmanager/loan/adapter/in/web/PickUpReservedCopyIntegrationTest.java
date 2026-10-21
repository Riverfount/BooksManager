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
 * Picking up the copy kept for a reservation (RN03, RN13, RF17, RF18) through the real path: security chain,
 * transaction and PostgreSQL, with no test transaction (it would hide the commit and the rollback). The data is
 * committed, so it is removed after each test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class PickUpReservedCopyIntegrationTest {

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
    private long ownerId;
    private long otherReaderId;
    private long copyId;

    @BeforeEach
    void createParents() {
        cleanUp();
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Pick up reserved test') returning id", Long.class);
        bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000300', 'T', ?) returning id",
                Long.class, categoryId);
        ownerId = newReader("PUR-DOC-1");
        otherReaderId = newReader("PUR-DOC-2");
        copyId = jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, 'PUR-1', 'RESERVED') returning id",
                Long.class, bookId);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from reservation where reader_id in (select id from reader where document like 'PUR-DOC-%')");
        jdbcTemplate.update("delete from loan where reader_id in (select id from reader where document like 'PUR-DOC-%')");
        jdbcTemplate.update("delete from copy where asset_code like 'PUR-%'");
        jdbcTemplate.update("delete from reader where document like 'PUR-DOC-%'");
        jdbcTemplate.update("delete from book where isbn = '9780000000300'");
        jdbcTemplate.update("delete from category where name = 'Pick up reserved test'");
    }

    private long newReader(String document) {
        return jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', ?, 'r@example.com', date '1990-01-01') returning id
                """, Long.class, document);
    }

    /** The reservation of the reader that keeps the copy until {@code deadline}. */
    private long reservationHoldingTheCopy(long reader, LocalDate deadline) {
        return jdbcTemplate.queryForObject("""
                insert into reservation (book_id, reader_id, reservation_date, status, copy_id, pickup_deadline)
                values (?, ?, ?, 'ACTIVE', ?, ?) returning id
                """, Long.class, bookId, reader, TODAY.minusDays(5), copyId, deadline);
    }

    private ResultActions lend(long reader) throws Exception {
        var token = "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", "OPERATOR", Instant.now(),
                Instant.now().plusSeconds(900));
        return mockMvc.perform(post("/api/v1/loans").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"copyId\":" + copyId + ",\"readerId\":" + reader + "}"));
    }

    private String copyStatus() {
        return jdbcTemplate.queryForObject("select status from copy where id = ?", String.class, copyId);
    }

    private String reservationStatus(long reservationId) {
        return jdbcTemplate.queryForObject("select status from reservation where id = ?", String.class,
                reservationId);
    }

    private int loans() {
        return jdbcTemplate.queryForObject("select count(*) from loan where copy_id = ?", Integer.class, copyId);
    }

    @Test
    void theOwnerShouldTakeTheReservedCopyWithinTheDeadline() throws Exception {
        var reservationId = reservationHoldingTheCopy(ownerId, TODAY.plusDays(2));

        lend(ownerId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.copyId").value(copyId))
                .andExpect(jsonPath("$.readerId").value(ownerId))
                .andExpect(jsonPath("$.active").value(true));

        assertThat(copyStatus()).isEqualTo("BORROWED");
        assertThat(reservationStatus(reservationId)).isEqualTo("FULFILLED");
        assertThat(loans()).isEqualTo(1);
    }

    @Test
    void theOwnerShouldTakeTheCopyOnTheLastDayOfTheDeadline() throws Exception {
        var reservationId = reservationHoldingTheCopy(ownerId, TODAY);

        lend(ownerId).andExpect(status().isCreated());

        assertThat(reservationStatus(reservationId)).isEqualTo("FULFILLED");
    }

    @Test
    void anotherReaderShouldBeRefusedWithTheSpecificMessage() throws Exception {
        var reservationId = reservationHoldingTheCopy(ownerId, TODAY.plusDays(2));

        lend(otherReaderId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("exemplar reservado para outro leitor")));

        assertThat(copyStatus()).isEqualTo("RESERVED");
        assertThat(reservationStatus(reservationId)).isEqualTo("ACTIVE");
        assertThat(loans()).isZero();
    }

    @Test
    void afterTheDeadlineNotEvenTheOwnerShouldTakeItEvenIfTheRoutineDidNotExpireTheReservationYet() throws Exception {
        var reservationId = reservationHoldingTheCopy(ownerId, TODAY.minusDays(1));

        lend(ownerId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("prazo de retirada")));

        assertThat(copyStatus()).isEqualTo("RESERVED");
        assertThat(reservationStatus(reservationId)).isEqualTo("ACTIVE");
        assertThat(loans()).isZero();
    }

    @Test
    void theOwnerShouldStillBeRefusedByTheOtherRules() throws Exception {
        var reservationId = reservationHoldingTheCopy(ownerId, TODAY.plusDays(2));
        jdbcTemplate.update("update reader set blocked = true where id = ?", ownerId);

        lend(ownerId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("o leitor está bloqueado")));

        assertThat(copyStatus()).isEqualTo("RESERVED");
        assertThat(reservationStatus(reservationId)).isEqualTo("ACTIVE");
    }

    @Test
    void aFailureWhileSavingTheLoanShouldUndoTheCopyAndTheReservation() throws Exception {
        // a stray active loan on the same copy makes the last step (saving the loan) fail on the unique index,
        // after the copy was marked borrowed and the reservation fulfilled: everything must roll back
        var reservationId = reservationHoldingTheCopy(ownerId, TODAY.plusDays(2));
        jdbcTemplate.update("insert into loan (copy_id, reader_id, pickup_date, due_date) values (?, ?, ?, ?)",
                copyId, otherReaderId, TODAY.minusDays(2), TODAY.plusDays(12));

        lend(ownerId).andExpect(status().is4xxClientError());

        assertThat(copyStatus()).isEqualTo("RESERVED");
        assertThat(reservationStatus(reservationId)).isEqualTo("ACTIVE");
        assertThat(loans()).isEqualTo(1);
    }

    @Test
    void twoSimultaneousPickUpsShouldLetOnlyOnePass() throws Exception {
        var reservationId = reservationHoldingTheCopy(ownerId, TODAY.plusDays(2));
        var startTogether = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        var results = new ArrayList<Future<Integer>>();
        try {
            for (var i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    startTogether.await(10, TimeUnit.SECONDS);
                    return lend(ownerId).andReturn().getResponse().getStatus();
                }));
            }
            var statuses = new ArrayList<Integer>();
            for (var result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses.stream().filter(s -> s == 201).count()).as("statuses %s", statuses).isEqualTo(1);
            assertThat(statuses).allMatch(s -> s == 201 || s == 409 || s == 422);
        } finally {
            pool.shutdownNow();
        }

        assertThat(loans()).isEqualTo(1);
        assertThat(copyStatus()).isEqualTo("BORROWED");
        assertThat(reservationStatus(reservationId)).isEqualTo("FULFILLED");
    }
}
