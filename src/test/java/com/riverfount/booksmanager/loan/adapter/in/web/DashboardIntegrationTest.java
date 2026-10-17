package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import java.time.Clock;
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
 * The dashboard (RF23, RF25) through the real security chain and PostgreSQL: each number is the one the
 * matching listing gives (copies, active loans, overdue loans), except the suspended readers, which are
 * counted by reader. Reads only, so each test runs in a transaction that is rolled back.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class DashboardIntegrationTest {

    private static final String URL = "/api/v1/reports/dashboard";
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    private long categoryId;
    private long emptyCategoryId;
    private LocalDate day;

    @BeforeEach
    void createTheLibrary() {
        day = LocalDate.now(clock);
        categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Dash Ficção') returning id", Long.class);
        emptyCategoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Dash Vazia') returning id", Long.class);
        var activeBook = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000200', 'A', ?) returning id",
                Long.class, categoryId);
        var inactiveBook = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id, active) values ('9780000000217', 'B', ?, false) "
                        + "returning id", Long.class, categoryId);
        var reader = reader("DASH-1");
        var otherReader = reader("DASH-2");

        // the copy of an inactive book still counts as a copy; an inactive copy does not
        var late = copy(activeBook, "DASH-C1", true);
        var onTime = copy(activeBook, "DASH-C2", true);
        var returned = copy(activeBook, "DASH-C3", true);
        copy(activeBook, "DASH-C4", true);
        copy(activeBook, "DASH-C5", false);
        copy(inactiveBook, "DASH-C6", true);

        loan(late, reader, day.minusDays(20), day.minusDays(6), null);
        loan(onTime, reader, day.minusDays(1), day.plusDays(13), null);
        var returnedLoan = loan(returned, otherReader, day.minusDays(30), day.minusDays(16), day.minusDays(10));

        // the same reader with two suspensions in force today, another reader with one, and one already over
        var firstLoan = loan(copy(activeBook, "DASH-C7", true), reader, day.minusDays(40), day.minusDays(26),
                day.minusDays(2));
        var secondLoan = loan(copy(activeBook, "DASH-C8", true), reader, day.minusDays(41), day.minusDays(27),
                day.minusDays(1));
        var otherLoan = loan(copy(activeBook, "DASH-C9", true), otherReader, day.minusDays(42),
                day.minusDays(28), day.minusDays(1));
        suspension(reader, firstLoan, day.minusDays(2), day.plusDays(3));
        suspension(reader, secondLoan, day.minusDays(1), day.plusDays(2));
        suspension(otherReader, otherLoan, day.minusDays(1), day.plusDays(1));
        suspension(otherReader, returnedLoan, day.minusDays(10), day.minusDays(5));
    }

    private long reader(String document) {
        return jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', ?, ?, date '1990-01-01') returning id
                """, Long.class, document, document.toLowerCase() + "@example.com");
    }

    private long copy(long bookId, String assetCode, boolean active) {
        return jdbcTemplate.queryForObject("""
                insert into copy (book_id, asset_code, status, active) values (?, ?, 'BORROWED', ?) returning id
                """, Long.class, bookId, assetCode, active);
    }

    private long loan(long copyId, long readerId, LocalDate pickup, LocalDate due, LocalDate returnedOn) {
        return jdbcTemplate.queryForObject("""
                insert into loan (copy_id, reader_id, pickup_date, due_date, return_date)
                values (?, ?, ?, ?, ?) returning id
                """, Long.class, copyId, readerId, pickup, due, returnedOn);
    }

    private void suspension(long readerId, long loanId, LocalDate start, LocalDate end) {
        jdbcTemplate.update("insert into suspension (reader_id, loan_id, start_date, end_date) values (?, ?, ?, ?)",
                readerId, loanId, start, end);
    }

    private ResultActions getAs(String url, String role) throws Exception {
        var token = "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", role, Instant.now(),
                Instant.now().plusSeconds(900));
        return mockMvc.perform(get(url).header("Authorization", token));
    }

    private long totalOf(String url) throws Exception {
        var body = getAs(url, "ADMIN").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("totalItems").asLong();
    }

    @Test
    void everyNumberShouldMatchTheTotalOfItsListing() throws Exception {
        var body = getAs(URL, "OPERATOR").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode dashboard = mapper.readTree(body);

        assertThat(dashboard.get("totalCopies").asLong()).isEqualTo(totalOf("/api/v1/copies"));
        assertThat(dashboard.get("activeLoans").asLong()).isEqualTo(totalOf("/api/v1/loans?status=ACTIVE"));
        assertThat(dashboard.get("overdueLoans").asLong()).isEqualTo(totalOf("/api/v1/loans?overdue=true"));
        assertThat(dashboard.get("overdueLoans").asLong()).isEqualTo(totalOf("/api/v1/reports/overdue-loans"));
    }

    @Test
    void theSuspendedReadersShouldBeCountedByReaderNotBySuspension() throws Exception {
        var suspensionsInForce = totalOf("/api/v1/suspensions?inForce=true");

        var body = getAs(URL, "ADMIN").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var suspendedReaders = mapper.readTree(body).get("suspendedReaders").asLong();

        // the fixture has two suspensions in force for one reader and one for another, plus one already over
        assertThat(suspensionsInForce).isGreaterThanOrEqualTo(3L);
        assertThat(suspendedReaders).isLessThan(suspensionsInForce);
        assertThat(suspendedReaders).isEqualTo(distinctReadersOf(suspensionsInForce));
    }

    private long distinctReadersOf(long expectedSuspensions) throws Exception {
        var body = getAs("/api/v1/suspensions?inForce=true&size=100", "ADMIN").andReturn().getResponse()
                .getContentAsString();
        var items = mapper.readTree(body).get("items");
        assertThat(items).hasSize((int) expectedSuspensions);
        return java.util.stream.StreamSupport.stream(items.spliterator(), false)
                .map(item -> item.get("readerId").asLong()).distinct().count();
    }

    @Test
    void theCategoriesShouldCountTheActiveBooksAndCopiesAndKeepAnEmptyOneAtZero() throws Exception {
        getAs(URL, "OPERATOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.copiesByCategory[?(@.categoryId == " + categoryId + ")].categoryName")
                        .value("Dash Ficção"))
                .andExpect(jsonPath("$.copiesByCategory[?(@.categoryId == " + categoryId + ")].books").value(1))
                // C1 to C4 and C7 to C9 of the active book, C6 of the inactive one; C5 is inactive
                .andExpect(jsonPath("$.copiesByCategory[?(@.categoryId == " + categoryId + ")].copies").value(8))
                .andExpect(jsonPath("$.copiesByCategory[?(@.categoryId == " + emptyCategoryId + ")].books")
                        .value(0))
                .andExpect(jsonPath("$.copiesByCategory[?(@.categoryId == " + emptyCategoryId + ")].copies")
                        .value(0));
    }

    @Test
    void theCopiesOfTheCategoriesShouldAddUpToTheTotal() throws Exception {
        var body = getAs(URL, "ADMIN").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var dashboard = mapper.readTree(body);

        var sum = java.util.stream.StreamSupport.stream(dashboard.get("copiesByCategory").spliterator(), false)
                .mapToLong(category -> category.get("copies").asLong()).sum();
        assertThat(sum).isEqualTo(dashboard.get("totalCopies").asLong());
    }

    @Test
    void withoutATokenTheAnswerShouldBe401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
