package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import java.time.Instant;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Registering a loan (RF11, HU07) with the real security chain and PostgreSQL. Each test runs
 * in a transaction that is rolled back; the rollback and the concurrency are tested apart,
 * without it (RegisterLoanConcurrencyTest).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class LoanIntegrationTest {

    private static final String URL = "/api/v1/loans";

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
    void createParents() {
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Loan integration test') returning id", Long.class);
        var bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000040', 'T', ?) returning id",
                Long.class, categoryId);
        copyId = jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, 'LI-1', 'AVAILABLE') returning id",
                Long.class, bookId);
        readerId = jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', 'LI-DOC-1', 'r@example.com', date '1990-01-01') returning id
                """, Long.class);
    }

    private String token(String role) {
        return "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", role, Instant.now(),
                Instant.now().plusSeconds(900));
    }

    private ResultActions register(String role, long copy, long reader) throws Exception {
        return mockMvc.perform(post(URL).header("Authorization", token(role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"copyId\":" + copy + ",\"readerId\":" + reader + "}"));
    }

    @Test
    void anOperatorShouldRegisterTheLoanAndTheCopyBecomesBorrowed() throws Exception {
        register("OPERATOR", copyId, readerId)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(URL + "/")))
                .andExpect(jsonPath("$.copyId").value(copyId))
                .andExpect(jsonPath("$.readerId").value(readerId))
                .andExpect(jsonPath("$.renewals").value(0))
                .andExpect(jsonPath("$.active").value(true));

        assertThat(jdbcTemplate.queryForObject("select status from copy where id = ?", String.class, copyId))
                .isEqualTo("BORROWED");
    }

    @Test
    void anAdministratorShouldAlsoRegisterTheLoan() throws Exception {
        register("ADMIN", copyId, readerId).andExpect(status().isCreated());
    }

    @Test
    void theDueDateShouldFollowThePeriodOfTheConfiguration() throws Exception {
        jdbcTemplate.update("update library_configuration set loan_period_days = 30");

        register("OPERATOR", copyId, readerId).andExpect(status().isCreated());

        var period = jdbcTemplate.queryForObject(
                "select due_date - pickup_date from loan where copy_id = ?", Integer.class, copyId);
        assertThat(period).isEqualTo(30);
    }

    @Test
    void aSecondLoanOfTheSameCopyShouldBeRefusedWith422() throws Exception {
        register("OPERATOR", copyId, readerId).andExpect(status().isCreated());
        var otherReader = jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Other', 'LI-DOC-2', 'o@example.com', date '1990-01-01') returning id
                """, Long.class);

        register("OPERATOR", copyId, otherReader)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("o exemplar não está disponível")));
    }

    @Test
    void aBlockedReaderShouldBeRefusedWith422AndTheCopyStaysAvailable() throws Exception {
        jdbcTemplate.update("update reader set blocked = true where id = ?", readerId);

        register("OPERATOR", copyId, readerId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("o leitor está bloqueado")));

        assertThat(jdbcTemplate.queryForObject("select status from copy where id = ?", String.class, copyId))
                .isEqualTo("AVAILABLE");
    }

    @Test
    void aReaderWithAPendingFineShouldBeRefusedWith422() throws Exception {
        var pastLoan = jdbcTemplate.queryForObject("""
                insert into loan (copy_id, reader_id, pickup_date, due_date, return_date, return_condition)
                values (?, ?, date '2026-01-01', date '2026-01-15', date '2026-01-18', 'GOOD') returning id
                """, Long.class, copyId, readerId);
        jdbcTemplate.update("insert into fine (loan_id, reader_id, amount, status) values (?, ?, 3.00, 'PENDING')",
                pastLoan, readerId);

        register("OPERATOR", copyId, readerId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("o leitor tem multa pendente")));
    }

    @Test
    void anUnknownCopyShouldReturn404() throws Exception {
        register("OPERATOR", -1L, readerId).andExpect(status().isNotFound());
    }

    @Test
    void anUnknownReaderShouldReturn404() throws Exception {
        register("OPERATOR", copyId, -1L).andExpect(status().isNotFound());
    }

    @Test
    void withoutATokenTheAnswerShouldBe401() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"copyId\":" + copyId + ",\"readerId\":" + readerId + "}"))
                .andExpect(status().isUnauthorized());
    }
}
