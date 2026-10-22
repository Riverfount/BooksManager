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
 * The most borrowed books report (RF24, RNF14) with the real security chain and PostgreSQL: the order from the
 * most to the least borrowed, the sum of the copies of a book, what is outside the period (both ends inclusive),
 * the renewal that is not a new loan, and the refusals of a bad period or page. Reads only, so each test runs in
 * a transaction that is rolled back.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class MostBorrowedBooksReportIntegrationTest {

    private static final String URL = "/api/v1/reports/most-borrowed-books";
    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    private long categoryId;
    private long readerId;
    private int sequence;

    @BeforeEach
    void createParents() {
        categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Most borrowed report test') returning id", Long.class);
        readerId = jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', 'MB-DOC-1', 'r@example.com', date '1990-01-01') returning id
                """, Long.class);
    }

    /** The 12 digits plus the ISBN-13 check digit: the catalog refuses a book whose ISBN does not check out. */
    private static String isbn(String twelveDigits) {
        var sum = 0;
        for (var i = 0; i < 12; i++) {
            sum += (twelveDigits.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return twelveDigits + (10 - sum % 10) % 10;
    }

    private long newBook(String title) {
        return jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values (?, ?, ?) returning id", Long.class,
                isbn("978000008" + String.format("%03d", ++sequence)), title, categoryId);
    }

    private long newCopy(long bookId) {
        return jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, ?, 'AVAILABLE') returning id", Long.class,
                bookId, "MB-" + ++sequence);
    }

    /** A returned loan of the copy, picked up on the day. */
    private void loan(long copyId, LocalDate pickup) {
        jdbcTemplate.update("""
                insert into loan (copy_id, reader_id, pickup_date, due_date, return_date, return_condition)
                values (?, ?, ?, ?, ?, 'GOOD')
                """, copyId, readerId, pickup, pickup.plusDays(14), pickup.plusDays(1));
    }

    private ResultActions report(String from, String to, String role) throws Exception {
        var token = "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", role, Instant.now(),
                Instant.now().plusSeconds(900));
        var request = get(URL).header("Authorization", token);
        if (from != null) {
            request = request.param("from", from);
        }
        if (to != null) {
            request = request.param("to", to);
        }
        return mockMvc.perform(request);
    }

    private ResultActions report() throws Exception {
        return report(FROM.toString(), TO.toString(), "OPERATOR");
    }

    @Test
    void shouldListTheBooksFromTheMostToTheLeastBorrowedSummingTheirCopies() throws Exception {
        var domCasmurro = newBook("Dom Casmurro");
        var iracema = newBook("Iracema");
        var quincas = newBook("Quincas Borba");
        var casmurroA = newCopy(domCasmurro);
        var casmurroB = newCopy(domCasmurro);
        var iracemaCopy = newCopy(iracema);
        var quincasCopy = newCopy(quincas);
        loan(casmurroA, FROM);
        loan(casmurroB, FROM.plusDays(2));
        loan(casmurroB, FROM.plusDays(9));
        loan(iracemaCopy, FROM.plusDays(1));
        loan(iracemaCopy, FROM.plusDays(5));
        loan(quincasCopy, FROM.plusDays(3));

        report()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].bookId").value(domCasmurro))
                .andExpect(jsonPath("$.items[0].title").value("Dom Casmurro"))
                .andExpect(jsonPath("$.items[0].totalLoans").value(3))
                .andExpect(jsonPath("$.items[1].bookId").value(iracema))
                .andExpect(jsonPath("$.items[1].totalLoans").value(2))
                .andExpect(jsonPath("$.items[2].bookId").value(quincas))
                .andExpect(jsonPath("$.items[2].totalLoans").value(1))
                .andExpect(jsonPath("$.totalItems").value(3));
    }

    @Test
    void loansOutsideThePeriodShouldNotCountAndBothEndsShouldBeInclusive() throws Exception {
        var book = newBook("Dom Casmurro");
        var copy = newCopy(book);
        loan(copy, FROM.minusDays(1));
        loan(copy, FROM);
        loan(copy, TO);
        loan(copy, TO.plusDays(1));

        report().andExpect(status().isOk()).andExpect(jsonPath("$.items[0].totalLoans").value(2));
    }

    @Test
    void aRenewalShouldNotCountAsANewLoan() throws Exception {
        var book = newBook("Dom Casmurro");
        var copy = newCopy(book);
        jdbcTemplate.update("""
                insert into loan (copy_id, reader_id, pickup_date, due_date, renewals) values (?, ?, ?, ?, 2)
                """, copy, readerId, FROM, FROM.plusDays(42));

        report().andExpect(status().isOk()).andExpect(jsonPath("$.items[0].totalLoans").value(1));
    }

    @Test
    void shouldBreakATieByTheTitle() throws Exception {
        var zebra = newBook("Zebra");
        var alpha = newBook("Alfa");
        loan(newCopy(zebra), FROM);
        loan(newCopy(alpha), FROM);

        report().andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Alfa"))
                .andExpect(jsonPath("$.items[1].title").value("Zebra"));
    }

    @Test
    void shouldPaginateTheReport() throws Exception {
        for (var i = 1; i <= 3; i++) {
            var copy = newCopy(newBook("Livro " + i));
            for (var n = 0; n < i; n++) {
                loan(copy, FROM.plusDays(n));
            }
        }

        mockMvc.perform(get(URL).param("from", FROM.toString()).param("to", TO.toString()).param("page", "1")
                        .param("size", "2").header("Authorization", "Bearer " + TestTokens.signed(encoder,
                                properties.issuer(), "1", "ADMIN", Instant.now(), Instant.now().plusSeconds(900))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Livro 1"))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void anEmptyPeriodShouldAnswerAnEmptyReport() throws Exception {
        loan(newCopy(newBook("Dom Casmurro")), FROM.minusDays(40));

        report().andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void aBadPeriodOrPageShouldAnswer400() throws Exception {
        report(null, null, "OPERATOR").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("período")));
        report(TO.toString(), FROM.toString(), "OPERATOR").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("invertido")));
        report("2025-01-01", "2026-12-31", "OPERATOR").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("366 dias")));
        report("not-a-date", TO.toString(), "OPERATOR").andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("from", FROM.toString()).param("to", TO.toString()).param("size", "101")
                        .header("Authorization", "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1",
                                "OPERATOR", Instant.now(), Instant.now().plusSeconds(900))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withoutATokenTheAnswerShouldBe401() throws Exception {
        mockMvc.perform(get(URL).param("from", FROM.toString()).param("to", TO.toString()))
                .andExpect(status().isUnauthorized());
    }
}
