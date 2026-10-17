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
 * Ending a suspension early (RN15, RF20) through the real path: security chain, clock,
 * transaction and PostgreSQL, with no test transaction (the data is committed, so it is removed
 * after each test). Includes the effect on new loans, and the race of two requests.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class EndSuspensionIntegrationTest {

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
    private long readerId;
    private int sequence;

    @BeforeEach
    void createASuspendedReader() {
        cleanUp();
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('End suspension test') returning id", Long.class);
        bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000118', 'T', ?) returning id",
                Long.class, categoryId);
        copyId = jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, 'ES-0', 'AVAILABLE') returning id",
                Long.class, bookId);
        readerId = jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', 'ES-DOC-1', 'r@example.com', date '1990-01-01') returning id
                """, Long.class);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from suspension where reader_id in (select id from reader where document like 'ES-DOC-%')");
        jdbcTemplate.update("delete from fine where reader_id in (select id from reader where document like 'ES-DOC-%')");
        jdbcTemplate.update("delete from loan where reader_id in (select id from reader where document like 'ES-DOC-%')");
        jdbcTemplate.update("delete from copy where asset_code like 'ES-%'");
        jdbcTemplate.update("delete from reader where document like 'ES-DOC-%'");
        jdbcTemplate.update("delete from book where isbn = '9780000000118'");
        jdbcTemplate.update("delete from category where name = 'End suspension test'");
    }

    /** A returned loan of the reader to hang a suspension or a fine on. */
    private long returnedLoan() {
        var pastCopyId = jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, ?, 'AVAILABLE') returning id",
                Long.class, bookId, "ES-" + ++sequence + "-" + sequence);
        return jdbcTemplate.queryForObject("""
                insert into loan (copy_id, reader_id, pickup_date, due_date, return_date)
                values (?, ?, date '2026-01-01', date '2026-01-15', date '2026-01-21') returning id
                """, Long.class, pastCopyId, readerId);
    }

    private long suspension(LocalDate start, LocalDate end) {
        return jdbcTemplate.queryForObject("""
                insert into suspension (reader_id, loan_id, start_date, end_date) values (?, ?, ?, ?) returning id
                """, Long.class, readerId, returnedLoan(), start, end);
    }

    private long runningSuspension() {
        return suspension(TODAY.minusDays(1), TODAY.plusDays(20));
    }

    private String token() {
        return "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", "OPERATOR", Instant.now(),
                Instant.now().plusSeconds(900));
    }

    private ResultActions end(long id, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/suspensions/" + id + "/end").header("Authorization", token())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions borrow() throws Exception {
        return mockMvc.perform(post("/api/v1/loans").header("Authorization", token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"copyId\":" + copyId + ",\"readerId\":" + readerId + "}"));
    }

    private String reasonInTheDatabase(long id) {
        return jdbcTemplate.queryForObject("select ended_early_reason from suspension where id = ?", String.class, id);
    }

    @Test
    void aSuspensionInForceShouldBlockTheLoanAndEndingItShouldUnblock() throws Exception {
        var id = runningSuspension();
        borrow().andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("o leitor está suspenso")));

        end(id, "{\"reason\":\"  Regularizado com a direção  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inForce").value(false))
                .andExpect(jsonPath("$.endedEarlyOn").value(TODAY.toString()))
                .andExpect(jsonPath("$.endedEarlyReason").value("Regularizado com a direção"))
                .andExpect(jsonPath("$.releaseDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.endDate").value(TODAY.plusDays(20).toString()));

        assertThat(reasonInTheDatabase(id)).isEqualTo("Regularizado com a direção");
        borrow().andExpect(status().isCreated());
    }

    @Test
    void endingTheSuspensionShouldNotHideAPendingFineThatStillBlocks() throws Exception {
        var id = runningSuspension();
        jdbcTemplate.update("insert into fine (loan_id, reader_id, amount, status) values (?, ?, 6.00, 'PENDING')",
                returnedLoan(), readerId);

        end(id, "{\"reason\":\"Pardoned\"}").andExpect(status().isOk());

        borrow().andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("multa pendente")))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(containsString("suspenso"))));
    }

    @Test
    void withoutAJustificationTheOperationShouldBeRefusedAndTheSuspensionKept() throws Exception {
        var id = runningSuspension();

        end(id, "{\"reason\":\"  \"}").andExpect(status().isBadRequest());
        end(id, "{}").andExpect(status().isBadRequest());

        assertThat(reasonInTheDatabase(id)).isNull();
        borrow().andExpect(status().isUnprocessableContent());
    }

    @Test
    void endingASuspensionThatAlreadyEndedShouldHaveNoEffect() throws Exception {
        var over = suspension(TODAY.minusDays(10), TODAY.minusDays(7));

        end(over, "{\"reason\":\"Too late\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inForce").value(false))
                .andExpect(jsonPath("$.endedEarlyOn").doesNotExist())
                .andExpect(jsonPath("$.endDate").value(TODAY.minusDays(7).toString()));

        assertThat(reasonInTheDatabase(over)).isNull();
    }

    @Test
    void endingASuspensionAlreadyEndedEarlyShouldKeepTheFirstJustification() throws Exception {
        var id = runningSuspension();
        end(id, "{\"reason\":\"First reason\"}").andExpect(status().isOk());

        end(id, "{\"reason\":\"Second reason\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endedEarlyReason").value("First reason"));

        assertThat(reasonInTheDatabase(id)).isEqualTo("First reason");
    }

    @Test
    void aSuspensionThatDoesNotExistShouldAnswer404() throws Exception {
        end(-1L, "{\"reason\":\"Reason\"}").andExpect(status().isNotFound());
    }

    @Test
    void withoutATokenTheAnswerShouldBe401() throws Exception {
        var id = runningSuspension();

        mockMvc.perform(post("/api/v1/suspensions/" + id + "/end").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Reason\"}")).andExpect(status().isUnauthorized());

        assertThat(reasonInTheDatabase(id)).isNull();
    }

    @Test
    void twoSimultaneousEndsShouldLeaveASingleJustification() throws Exception {
        var id = runningSuspension();
        var startTogether = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        var results = new ArrayList<Future<Integer>>();
        var statuses = new ArrayList<Integer>();
        try {
            for (var reason : new String[] {"Reason A", "Reason B"}) {
                results.add(pool.submit(() -> {
                    startTogether.await(10, TimeUnit.SECONDS);
                    return end(id, "{\"reason\":\"" + reason + "\"}").andReturn().getResponse().getStatus();
                }));
            }
            for (var result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        // one ends it; the other either sees it already ended (200, no effect) or loses the race (409)
        assertThat(statuses).allMatch(s -> s == 200 || s == 409);
        assertThat(statuses).contains(200);
        assertThat(reasonInTheDatabase(id)).isIn("Reason A", "Reason B");
        assertThat(jdbcTemplate.queryForObject("select ended_early_on from suspension where id = ?",
                java.sql.Date.class, id)).isNotNull();
    }
}
