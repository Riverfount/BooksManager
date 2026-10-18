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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Renewing a loan (RF13, HU09) through the real path: security chain, transaction and
 * PostgreSQL, with no test transaction (the data is committed, so it is removed after each
 * test and the configuration is restored).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class RenewLoanIntegrationTest {

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
    private long readerId;
    private int sequence;

    @BeforeEach
    void createParents() {
        cleanUp();
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Renew loan test') returning id", Long.class);
        bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000064', 'T', ?) returning id",
                Long.class, categoryId);
        readerId = jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', 'RNW-DOC-1', 'r@example.com', date '1990-01-01') returning id
                """, Long.class);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from loan where reader_id in (select id from reader where document like 'RNW-DOC-%')");
        jdbcTemplate.update("delete from copy where asset_code like 'RNW-%'");
        jdbcTemplate.update("delete from reader where document like 'RNW-DOC-%'");
        jdbcTemplate.update("delete from book where isbn = '9780000000064'");
        jdbcTemplate.update("delete from category where name = 'Renew loan test'");
        jdbcTemplate.update("""
                update library_configuration set penalty_policy = 'FINE', daily_fine_amount = 1.00,
                    fine_cap = 10.00, suspension_days_per_day = 1, suspension_cap_days = 365,
                    loan_period_days = 14, loan_limit = 3, renewal_limit = 2, reservation_pickup_days = 3
                """);
    }

    /** An active loan picked up {@code daysAgo} days ago for 14 days. */
    private long activeLoan(int daysAgo) {
        var copyId = jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, ?, 'BORROWED') returning id",
                Long.class, bookId, "RNW-" + ++sequence);
        var pickup = TODAY.minusDays(daysAgo);
        return jdbcTemplate.queryForObject("""
                insert into loan (copy_id, reader_id, pickup_date, due_date) values (?, ?, ?, ?) returning id
                """, Long.class, copyId, readerId, pickup, pickup.plusDays(14));
    }

    private ResultActions renew(long loanId) throws Exception {
        var token = "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", "OPERATOR", Instant.now(),
                Instant.now().plusSeconds(900));
        return mockMvc.perform(post("/api/v1/loans/" + loanId + "/renew").header("Authorization", token));
    }

    private int renewalsOf(long loanId) {
        return jdbcTemplate.queryForObject("select renewals from loan where id = ?", Integer.class, loanId);
    }

    @Test
    void shouldRenewAndMoveTheDueDateByTheConfiguredPeriodFromTheCurrentDueDate() throws Exception {
        jdbcTemplate.update("update library_configuration set loan_period_days = 21");
        var loanId = activeLoan(10); // due in 4 days

        renew(loanId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.renewals").value(1))
                .andExpect(jsonPath("$.dueDate").value(TODAY.plusDays(4).plusDays(21).toString()));

        assertThat(renewalsOf(loanId)).isEqualTo(1);
    }

    @Test
    void shouldRefuseAboveTheLimitOfTheConfiguration() throws Exception {
        jdbcTemplate.update("update library_configuration set renewal_limit = 1");
        var loanId = activeLoan(5);
        renew(loanId).andExpect(status().isOk());

        renew(loanId).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("limite de renovações (1)")));

        assertThat(renewalsOf(loanId)).isEqualTo(1);
    }

    @Test
    void shouldRefuseALateLoan() throws Exception {
        var loanId = activeLoan(20);

        renew(loanId).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("em atraso")));

        assertThat(renewalsOf(loanId)).isZero();
    }

    @Test
    void shouldRefuseAClosedLoan() throws Exception {
        var loanId = activeLoan(5);
        jdbcTemplate.update("update loan set return_date = ?, return_condition = 'GOOD' where id = ?", TODAY, loanId);

        renew(loanId).andExpect(status().isUnprocessableContent());

        assertThat(renewalsOf(loanId)).isZero();
    }

    @Test
    void anUnknownLoanShouldAnswer404() throws Exception {
        renew(-1L).andExpect(status().isNotFound());
    }

    @Test
    void withoutATokenTheAnswerShouldBe401() throws Exception {
        mockMvc.perform(post("/api/v1/loans/" + activeLoan(5) + "/renew")).andExpect(status().isUnauthorized());
    }

    @Test
    void twoSimultaneousRenewalsShouldNeverLoseOne() throws Exception {
        var loanId = activeLoan(5);
        var startTogether = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        var results = new ArrayList<Future<Integer>>();
        var statuses = new ArrayList<Integer>();
        try {
            for (var i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    startTogether.await(10, TimeUnit.SECONDS);
                    return renew(loanId).andReturn().getResponse().getStatus();
                }));
            }
            for (var result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        // both may pass one after the other (the limit is 2), or one loses the race with a 409;
        // what can never happen is an answer 200 whose renewal was overwritten
        assertThat(statuses).allMatch(s -> s == 200 || s == 409);
        assertThat(renewalsOf(loanId)).as("statuses %s", statuses)
                .isEqualTo((int) statuses.stream().filter(s -> s == 200).count());
        assertThat(statuses).contains(200);
    }
}
