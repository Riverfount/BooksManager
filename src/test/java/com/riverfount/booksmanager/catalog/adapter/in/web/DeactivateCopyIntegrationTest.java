package com.riverfount.booksmanager.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * Deactivating a copy (RN10, RN14) through the real path, now that the catalog asks the loan
 * module whether the copy has an active loan: security chain, transaction and PostgreSQL, with
 * no test transaction (the data is committed, so it is removed after each test). Includes the
 * race between deactivating a copy and lending it.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class DeactivateCopyIntegrationTest {

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

    @BeforeEach
    void createACopyAndAReader() {
        cleanUp();
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Deactivate copy test') returning id", Long.class);
        var bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000132', 'T', ?) returning id",
                Long.class, categoryId);
        copyId = jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, 'DC-1', 'AVAILABLE') returning id",
                Long.class, bookId);
        readerId = jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', 'DC-DOC-1', 'r@example.com', date '1990-01-01') returning id
                """, Long.class);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from loan where reader_id in (select id from reader where document like 'DC-DOC-%')");
        jdbcTemplate.update("delete from copy where asset_code like 'DC-%'");
        jdbcTemplate.update("delete from reader where document like 'DC-DOC-%'");
        jdbcTemplate.update("delete from book where isbn = '9780000000132'");
        jdbcTemplate.update("delete from category where name = 'Deactivate copy test'");
    }

    private String token() {
        return "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", "OPERATOR", Instant.now(),
                Instant.now().plusSeconds(900));
    }

    private ResultActions deactivate() throws Exception {
        return mockMvc.perform(delete("/api/v1/copies/" + copyId).header("Authorization", token()));
    }

    private ResultActions borrow() throws Exception {
        return mockMvc.perform(post("/api/v1/loans").header("Authorization", token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"copyId\":" + copyId + ",\"readerId\":" + readerId + "}"));
    }

    private ResultActions giveBack(long loanId) throws Exception {
        return mockMvc.perform(post("/api/v1/loans/" + loanId + "/return").header("Authorization", token()));
    }

    private boolean copyIsActive() {
        return jdbcTemplate.queryForObject("select active from copy where id = ?", Boolean.class, copyId);
    }

    @Test
    void aCopyWithNoLoanShouldBeDeactivated() throws Exception {
        deactivate().andExpect(status().isNoContent());

        assertThat(copyIsActive()).isFalse();
    }

    @Test
    void aCopyOnLoanShouldNotBeDeactivatedUntilItIsReturned() throws Exception {
        borrow().andExpect(status().isCreated());

        deactivate().andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("empréstimo ativo")));
        assertThat(copyIsActive()).isTrue();

        var loanId = jdbcTemplate.queryForObject("select id from loan where copy_id = ?", Long.class, copyId);
        giveBack(loanId).andExpect(status().isOk());
        deactivate().andExpect(status().isNoContent());
        assertThat(copyIsActive()).isFalse();
    }

    @Test
    void anActiveLoanThatTheStatusOfTheCopyDoesNotShowShouldStillBlockTheDeactivation() throws Exception {
        // the copy says AVAILABLE, but the loan is open: the loan is the source of truth
        jdbcTemplate.update("""
                insert into loan (copy_id, reader_id, pickup_date, due_date) values (?, ?, current_date, current_date + 14)
                """, copyId, readerId);

        deactivate().andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("empréstimo ativo")));

        assertThat(copyIsActive()).isTrue();
    }

    @Test
    void aCopyThatDoesNotExistShouldStillAnswer404() throws Exception {
        mockMvc.perform(delete("/api/v1/copies/-1").header("Authorization", token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivatingAnAlreadyInactiveCopyShouldStillBeIdempotent() throws Exception {
        deactivate().andExpect(status().isNoContent());

        deactivate().andExpect(status().isNoContent());
    }

    @Test
    void deactivatingAndLendingAtTheSameTimeShouldNeverLeaveAnInactiveCopyOnLoan() throws Exception {
        var startTogether = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        var results = new ArrayList<Future<Integer>>();
        var statuses = new ArrayList<Integer>();
        try {
            results.add(pool.submit(() -> {
                startTogether.await(10, TimeUnit.SECONDS);
                return deactivate().andReturn().getResponse().getStatus();
            }));
            results.add(pool.submit(() -> {
                startTogether.await(10, TimeUnit.SECONDS);
                return borrow().andReturn().getResponse().getStatus();
            }));
            for (var result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        var loans = jdbcTemplate.queryForObject("select count(*) from loan where copy_id = ?", Integer.class, copyId);
        // either the copy was lent (and so stays active) or it was deactivated (and so was not lent)
        assertThat(copyIsActive() && loans == 1 || !copyIsActive() && loans == 0)
                .as("statuses %s, active %s, loans %s", statuses, copyIsActive(), loans).isTrue();
        assertThat(statuses).allMatch(s -> s == 201 || s == 204 || s == 409 || s == 422);
    }
}
