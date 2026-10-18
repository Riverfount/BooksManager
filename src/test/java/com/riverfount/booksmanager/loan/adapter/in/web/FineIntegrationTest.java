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
 * Paying and waiving a fine (RF21, HU10, RN05) through the real path: security chain,
 * transaction and PostgreSQL, with no test transaction (the data is committed, so it is removed
 * after each test). Includes the effect on new loans and the race of two payments.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class FineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    private long copyId;
    private long readerId;
    private long fineId;

    @BeforeEach
    void createAFinedReader() {
        cleanUp();
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Fine test') returning id", Long.class);
        var bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000095', 'T', ?) returning id",
                Long.class, categoryId);
        copyId = jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, 'FN-1', 'AVAILABLE') returning id",
                Long.class, bookId);
        readerId = jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', 'FN-DOC-1', 'r@example.com', date '1990-01-01') returning id
                """, Long.class);
        var pastCopyId = jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, 'FN-2', 'AVAILABLE') returning id",
                Long.class, bookId);
        var pastLoanId = jdbcTemplate.queryForObject("""
                insert into loan (copy_id, reader_id, pickup_date, due_date, return_date, return_condition)
                values (?, ?, date '2026-01-01', date '2026-01-15', date '2026-01-21', 'GOOD') returning id
                """, Long.class, pastCopyId, readerId);
        fineId = jdbcTemplate.queryForObject(
                "insert into fine (loan_id, reader_id, amount, status) values (?, ?, 6.00, 'PENDING') returning id",
                Long.class, pastLoanId, readerId);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from fine where reader_id in (select id from reader where document like 'FN-DOC-%')");
        jdbcTemplate.update("delete from loan where reader_id in (select id from reader where document like 'FN-DOC-%')");
        jdbcTemplate.update("delete from copy where asset_code like 'FN-%'");
        jdbcTemplate.update("delete from reader where document like 'FN-DOC-%'");
        jdbcTemplate.update("delete from book where isbn = '9780000000095'");
        jdbcTemplate.update("delete from category where name = 'Fine test'");
    }

    private String token() {
        return "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", "OPERATOR", Instant.now(),
                Instant.now().plusSeconds(900));
    }

    private ResultActions pay(long id) throws Exception {
        return mockMvc.perform(post("/api/v1/fines/" + id + "/pay").header("Authorization", token()));
    }

    private ResultActions waive(long id, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/fines/" + id + "/waive").header("Authorization", token())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions borrow() throws Exception {
        return mockMvc.perform(post("/api/v1/loans").header("Authorization", token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"copyId\":" + copyId + ",\"readerId\":" + readerId + "}"));
    }

    private String statusInTheDatabase() {
        return jdbcTemplate.queryForObject("select status from fine where id = ?", String.class, fineId);
    }

    // RN05: a pending fine blocks, a resolved one stops blocking

    @Test
    void aPendingFineShouldBlockTheLoanAndPayingItShouldUnblock() throws Exception {
        borrow().andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("multa pendente")));

        pay(fineId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));

        assertThat(statusInTheDatabase()).isEqualTo("PAID");
        borrow().andExpect(status().isCreated());
    }

    @Test
    void waivingTheFineShouldAlsoUnblockTheLoanAndKeepTheJustification() throws Exception {
        waive(fineId, "{\"reason\":\"  Internação do leitor  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAIVED"))
                .andExpect(jsonPath("$.waiverReason").value("Internação do leitor"));

        assertThat(jdbcTemplate.queryForObject("select waiver_reason from fine where id = ?", String.class, fineId))
                .isEqualTo("Internação do leitor");
        borrow().andExpect(status().isCreated());
    }

    // refusals

    @Test
    void waivingWithoutAJustificationShouldAnswer400AndKeepTheFinePending() throws Exception {
        waive(fineId, "{\"reason\":\"  \"}").andExpect(status().isBadRequest());
        waive(fineId, "{}").andExpect(status().isBadRequest());

        assertThat(statusInTheDatabase()).isEqualTo("PENDING");
        borrow().andExpect(status().isUnprocessableContent());
    }

    @Test
    void aFineAlreadyResolvedShouldAnswer422ForPayingAndForWaiving() throws Exception {
        pay(fineId).andExpect(status().isOk());

        pay(fineId).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("já foi paga")));
        waive(fineId, "{\"reason\":\"Late reason\"}").andExpect(status().isUnprocessableContent());

        assertThat(statusInTheDatabase()).isEqualTo("PAID");
        assertThat(jdbcTemplate.queryForObject("select waiver_reason from fine where id = ?", String.class, fineId))
                .isNull();
    }

    @Test
    void aWaivedFineShouldNotBePaidAndKeepsItsFirstJustification() throws Exception {
        waive(fineId, "{\"reason\":\"First reason\"}").andExpect(status().isOk());

        pay(fineId).andExpect(status().isUnprocessableContent());
        waive(fineId, "{\"reason\":\"Second reason\"}").andExpect(status().isUnprocessableContent());

        assertThat(jdbcTemplate.queryForObject("select waiver_reason from fine where id = ?", String.class, fineId))
                .isEqualTo("First reason");
    }

    @Test
    void aFineThatDoesNotExistShouldAnswer404() throws Exception {
        pay(-1L).andExpect(status().isNotFound());
        waive(-1L, "{\"reason\":\"Reason\"}").andExpect(status().isNotFound());
    }

    @Test
    void withoutATokenTheAnswerShouldBe401() throws Exception {
        mockMvc.perform(post("/api/v1/fines/" + fineId + "/pay")).andExpect(status().isUnauthorized());
        assertThat(statusInTheDatabase()).isEqualTo("PENDING");
    }

    // the race

    @Test
    void aPaymentAndAWaiverAtTheSameTimeShouldLetOnlyOnePass() throws Exception {
        var startTogether = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        var results = new ArrayList<Future<Integer>>();
        var statuses = new ArrayList<Integer>();
        try {
            results.add(pool.submit(() -> {
                startTogether.await(10, TimeUnit.SECONDS);
                return pay(fineId).andReturn().getResponse().getStatus();
            }));
            results.add(pool.submit(() -> {
                startTogether.await(10, TimeUnit.SECONDS);
                return waive(fineId, "{\"reason\":\"Race\"}").andReturn().getResponse().getStatus();
            }));
            for (var result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(statuses.stream().filter(s -> s == 200).count()).as("statuses %s", statuses).isEqualTo(1);
        assertThat(statuses).allMatch(s -> s == 200 || s == 409 || s == 422);
        var finalStatus = statusInTheDatabase();
        var reason = jdbcTemplate.queryForObject("select waiver_reason from fine where id = ?", String.class, fineId);
        assertThat(finalStatus).isIn("PAID", "WAIVED");
        assertThat(reason != null).as("the reason exists if and only if it was waived")
                .isEqualTo(finalStatus.equals("WAIVED"));
    }
}
