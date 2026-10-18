package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * The overdue loans report (RF23, HU12, RNF14) with the real security chain, clock and PostgreSQL:
 * the order from the most to the least late, what does not appear (on time, returned), the reader
 * and the copy of each line, and the page limit. Reads only, so each test runs in a transaction that
 * is rolled back.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class OverdueLoansReportIntegrationTest {

    private static final String URL = "/api/v1/reports/overdue-loans";
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
    private int sequence;

    @BeforeEach
    void createABook() {
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Overdue report test') returning id", Long.class);
        bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000149', 'Dom Casmurro', ?) returning id",
                Long.class, categoryId);
    }

    private long newReader(String name) {
        return jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values (?, ?, 'r@example.com', date '1990-01-01') returning id
                """, Long.class, name, "OR-DOC-" + ++sequence);
    }

    /** A loan due {@code lateByDays} days ago (negative: due in the future), returned or not. */
    private long loan(long readerId, int lateByDays, boolean returned) {
        var copyId = jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, ?, 'BORROWED') returning id",
                Long.class, bookId, "OR-" + ++sequence);
        var due = TODAY.minusDays(lateByDays);
        return jdbcTemplate.queryForObject("""
                insert into loan (copy_id, reader_id, pickup_date, due_date, return_date, return_condition)
                values (?, ?, ?, ?, ?, case when ?::date is null then null else 'GOOD' end) returning id
                """, Long.class, copyId, readerId, due.minusDays(14), due, returned ? due.minusDays(1) : null,
                returned ? due.minusDays(1) : null);
    }

    private ResultActions report(String query) throws Exception {
        var token = "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", "OPERATOR", Instant.now(),
                Instant.now().plusSeconds(900));
        return mockMvc.perform(get(URL + query).header("Authorization", token));
    }

    @Test
    void shouldListTheLateLoansFromTheMostToTheLeastLateWithTheReaderAndTheCopy() throws Exception {
        var maria = newReader("Maria Silva");
        var joao = newReader("João Souza");
        var least = loan(maria, 2, false);
        var most = loan(joao, 26, false);
        var middle = loan(maria, 11, false);

        report("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[0].loanId").value(most))
                .andExpect(jsonPath("$.items[0].daysLate").value(26))
                .andExpect(jsonPath("$.items[0].readerId").value(joao))
                .andExpect(jsonPath("$.items[0].readerName").value("João Souza"))
                .andExpect(jsonPath("$.items[0].bookId").value(bookId))
                .andExpect(jsonPath("$.items[0].bookTitle").value("Dom Casmurro"))
                .andExpect(jsonPath("$.items[0].assetCode").exists())
                .andExpect(jsonPath("$.items[0].dueDate").value(TODAY.minusDays(26).toString()))
                .andExpect(jsonPath("$.items[1].loanId").value(middle))
                .andExpect(jsonPath("$.items[1].readerName").value("Maria Silva"))
                .andExpect(jsonPath("$.items[1].daysLate").value(11))
                .andExpect(jsonPath("$.items[2].loanId").value(least))
                .andExpect(jsonPath("$.items[2].daysLate").value(2));
    }

    @Test
    void aLoanOnTimeOrAlreadyReturnedShouldNotAppear() throws Exception {
        var reader = newReader("Maria Silva");
        loan(reader, 0, false);    // due today
        loan(reader, -5, false);   // due in 5 days
        loan(reader, 30, true);    // was very late but came back
        var late = loan(reader, 1, false);

        report("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].loanId").value(late))
                .andExpect(jsonPath("$.items[0].daysLate").value(1));
    }

    @Test
    void shouldPaginateKeepingTheOrder() throws Exception {
        var reader = newReader("Maria Silva");
        for (var late = 1; late <= 5; late++) {
            loan(reader, late, false);
        }

        report("?size=2&page=1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].daysLate").value(3))
                .andExpect(jsonPath("$.items[1].daysLate").value(2))
                .andExpect(jsonPath("$.totalItems").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void aPageSizeAbove100ShouldAnswer400() throws Exception {
        report("?size=101").andExpect(status().isBadRequest());
        report("?size=100").andExpect(status().isOk());
    }

    @Test
    void anEmptyReportShouldBeAnEmptyPage() throws Exception {
        report("").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void withoutATokenTheAnswerShouldBe401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
