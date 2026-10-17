package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Querying loans (RF14, HU30, RNF14) with the real security chain and PostgreSQL: the history of
 * a copy and of a reader, the separation of active and closed, the penalty of a closed loan and
 * the page size limit. Reads only, so each test runs in a transaction that is rolled back.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class LoanQueryIntegrationTest {

    private static final String URL = "/api/v1/loans";
    private static final LocalDate PICKUP = LocalDate.of(2026, 1, 1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private java.time.Clock clock;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    private long copyId;
    private long readerId;
    private long returnedLoanId;
    private long activeLoanId;

    @BeforeEach
    void createLoans() {
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Loan query test') returning id", Long.class);
        var bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000088', 'T', ?) returning id",
                Long.class, categoryId);
        copyId = jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, 'LQ-1', 'BORROWED') returning id",
                Long.class, bookId);
        readerId = jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', 'LQ-DOC-1', 'r@example.com', date '1990-01-01') returning id
                """, Long.class);
        // the same copy, first returned 6 days late (with its penalty), then borrowed again and still active
        returnedLoanId = jdbcTemplate.queryForObject("""
                insert into loan (copy_id, reader_id, pickup_date, due_date, return_date)
                values (?, ?, ?, ?, ?) returning id
                """, Long.class, copyId, readerId, PICKUP, PICKUP.plusDays(14), PICKUP.plusDays(20));
        jdbcTemplate.update("insert into fine (loan_id, reader_id, amount, status) values (?, ?, 6.00, 'PENDING')",
                returnedLoanId, readerId);
        jdbcTemplate.update("insert into suspension (reader_id, loan_id, start_date, end_date) values (?, ?, ?, ?)",
                readerId, returnedLoanId, PICKUP.plusDays(20), PICKUP.plusDays(26));
        activeLoanId = jdbcTemplate.queryForObject("""
                insert into loan (copy_id, reader_id, pickup_date, due_date) values (?, ?, ?, ?) returning id
                """, Long.class, copyId, readerId, PICKUP.plusDays(30), PICKUP.plusDays(44));
    }

    /** An active loan needs a copy of its own (one active loan per copy). */
    private long anotherCopy(String assetCode) {
        return jdbcTemplate.queryForObject("""
                insert into copy (book_id, asset_code, status)
                values ((select book_id from copy where id = ?), ?, 'BORROWED') returning id
                """, Long.class, copyId, assetCode);
    }

    private ResultActions getAs(String url, String role) throws Exception {
        var token = "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", role, Instant.now(),
                Instant.now().plusSeconds(900));
        return mockMvc.perform(get(url).header("Authorization", token));
    }

    @Test
    void aClosedLoanShouldStayConsultableWithItsDatesAndPenalty() throws Exception {
        getAs(URL + "/" + returnedLoanId, "OPERATOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.copyId").value(copyId))
                .andExpect(jsonPath("$.pickupDate").value("2026-01-01"))
                .andExpect(jsonPath("$.dueDate").value("2026-01-15"))
                .andExpect(jsonPath("$.returnDate").value("2026-01-21"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.daysLate").value(6))
                .andExpect(jsonPath("$.fine.amount").value(6.00))
                .andExpect(jsonPath("$.fine.status").value("PENDING"))
                .andExpect(jsonPath("$.suspension.startDate").value("2026-01-21"))
                .andExpect(jsonPath("$.suspension.endDate").value("2026-01-27"));
    }

    @Test
    void anActiveLoanShouldShowNoReturnNorPenalty() throws Exception {
        getAs(URL + "/" + activeLoanId, "ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.returnDate").doesNotExist())
                .andExpect(jsonPath("$.fine").doesNotExist())
                .andExpect(jsonPath("$.suspension").doesNotExist());
    }

    @Test
    void anActiveLoanPastItsDueDateShouldShowTheDaysLateOfToday() throws Exception {
        var days = java.time.temporal.ChronoUnit.DAYS.between(PICKUP.plusDays(44), LocalDate.now(clock));

        getAs(URL + "/" + activeLoanId, "ADMIN")
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.daysLate").value(days));
    }

    @Test
    void theOverdueFilterShouldGiveTheSameLoansAndTotalAsTheOverdueReport() throws Exception {
        var today = LocalDate.now(clock);
        var onTimeId = jdbcTemplate.queryForObject("""
                insert into loan (copy_id, reader_id, pickup_date, due_date) values (?, ?, ?, ?) returning id
                """, Long.class, anotherCopy("LQ-2"), readerId, today.minusDays(2), today.plusDays(12));
        var dueTodayId = jdbcTemplate.queryForObject("""
                insert into loan (copy_id, reader_id, pickup_date, due_date) values (?, ?, ?, ?) returning id
                """, Long.class, anotherCopy("LQ-3"), readerId, today.minusDays(14), today);

        var report = getAs("/api/v1/reports/overdue-loans", "OPERATOR").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var reportTotal = new com.fasterxml.jackson.databind.ObjectMapper().readTree(report).get("totalItems").asLong();

        getAs(URL + "?overdue=true", "OPERATOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(reportTotal))
                .andExpect(jsonPath("$.items[?(@.id == " + activeLoanId + ")]").exists())
                .andExpect(jsonPath("$.items[?(@.id == " + returnedLoanId + ")]").doesNotExist())
                .andExpect(jsonPath("$.items[?(@.id == " + onTimeId + ")]").doesNotExist())
                .andExpect(jsonPath("$.items[?(@.id == " + dueTodayId + ")]").doesNotExist());
    }

    @Test
    void overdueTogetherWithReturnedShouldAnswer400() throws Exception {
        getAs(URL + "?overdue=true&status=RETURNED", "OPERATOR").andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownLoanShouldAnswer404() throws Exception {
        getAs(URL + "/-1", "OPERATOR").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(containsString("-1")));
    }

    @Test
    void theHistoryOfACopyShouldHaveTheClosedAndTheActiveLoanMostRecentFirst() throws Exception {
        getAs(URL + "?copyId=" + copyId, "OPERATOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].id").value(activeLoanId))
                .andExpect(jsonPath("$.items[1].id").value(returnedLoanId))
                .andExpect(jsonPath("$.items[1].fine.id").exists());
    }

    @Test
    void shouldSeparateActiveFromClosedForAReader() throws Exception {
        getAs(URL + "?readerId=" + readerId + "&status=ACTIVE", "OPERATOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(activeLoanId));
        getAs(URL + "?readerId=" + readerId + "&status=RETURNED", "OPERATOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(returnedLoanId));
    }

    @Test
    void shouldPaginateAndSort() throws Exception {
        getAs(URL + "?copyId=" + copyId + "&size=1&page=1&sort=pickupDate,desc", "OPERATOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(returnedLoanId))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void aPageSizeAbove100ShouldAnswer400() throws Exception {
        getAs(URL + "?size=101", "OPERATOR").andExpect(status().isBadRequest());
    }

    @Test
    void withoutATokenTheAnswerShouldBe401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
