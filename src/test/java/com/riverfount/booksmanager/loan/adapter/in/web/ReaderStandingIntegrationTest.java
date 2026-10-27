package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.hibernate.SessionFactory;
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
 * How the reader stands (Phase 6), through the real path (security chain, transaction and PostgreSQL, no test
 * transaction), with two readers: each one sees only their own state, a blocked, suspended or fined reader reads
 * normally, an administrator is kept out, and the number of queries does not grow with what the reader owes.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ReaderStandingIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private long bookId;
    private long ana;
    private long bia;
    private int sequence;

    @BeforeEach
    void createTheBookAndTwoReaders() {
        cleanUp();
        jdbcTemplate.update("update library_configuration set loan_limit = 3");
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('RST test') returning id", Long.class);
        bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000007162', 'RST Dom Casmurro', ?) returning id",
                Long.class, categoryId);
        ana = newReader("Ana");
        bia = newReader("Bia");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from reservation where reader_id in (select id from reader where document like 'RST-DOC-%')");
        jdbcTemplate.update("delete from fine where reader_id in (select id from reader where document like 'RST-DOC-%')");
        jdbcTemplate.update("delete from suspension where reader_id in (select id from reader where document like 'RST-DOC-%')");
        jdbcTemplate.update("delete from loan where reader_id in (select id from reader where document like 'RST-DOC-%')");
        jdbcTemplate.update("delete from copy where asset_code like 'RST-%'");
        jdbcTemplate.update("delete from reader where document like 'RST-DOC-%'");
        jdbcTemplate.update("delete from book where isbn = '9780000007162'");
        jdbcTemplate.update("delete from category where name = 'RST test'");
        jdbcTemplate.update("update library_configuration set loan_limit = 3");
    }

    private long newReader(String name) {
        return jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values (?, ?, 'r@example.com', date '1990-01-01') returning id
                """, Long.class, name, "RST-DOC-" + ++sequence);
    }

    private long newCopy(String status) {
        return jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, ?, ?) returning id", Long.class, bookId,
                "RST-" + ++sequence, status);
    }

    private long activeLoan(long readerId, int daysAgo, int dueInDays) {
        return jdbcTemplate.queryForObject("""
                insert into loan (copy_id, reader_id, pickup_date, due_date) values (?, ?, ?, ?) returning id
                """, Long.class, newCopy("BORROWED"), readerId, TODAY.minusDays(daysAgo), TODAY.plusDays(dueInDays));
    }

    /** A returned loan whose return left a pending fine of the given amount. */
    private long returnedLoanWithFine(long readerId, String amount) {
        var loan = jdbcTemplate.queryForObject("""
                insert into loan (copy_id, reader_id, pickup_date, due_date, return_date, return_condition)
                values (?, ?, ?, ?, ?, 'GOOD') returning id
                """, Long.class, newCopy("AVAILABLE"), readerId, TODAY.minusDays(30), TODAY.minusDays(16),
                TODAY.minusDays(10));
        jdbcTemplate.update("insert into fine (loan_id, reader_id, amount, status) values (?, ?, ?::numeric, 'PENDING')",
                loan, readerId, amount);
        return loan;
    }

    private String asReader(long readerId) {
        return "Bearer " + TestTokens.forReader(encoder, properties, 1000 + readerId, readerId);
    }

    private ResultActions standing(long readerId) throws Exception {
        return mockMvc.perform(get("/api/v1/me/standing").header("Authorization", asReader(readerId)));
    }

    @Test
    void aReaderInGoodStandingShouldBeToldTheyCanBorrow() throws Exception {
        activeLoan(bia, 5, 9);

        standing(bia).andExpect(status().isOk())
                .andExpect(jsonPath("$.canBorrow").value(true))
                .andExpect(jsonPath("$.blocked").value(false))
                .andExpect(jsonPath("$.reasons").isEmpty())
                .andExpect(jsonPath("$.pendingFineTotal").value(0))
                .andExpect(jsonPath("$.suspension").doesNotExist())
                .andExpect(jsonPath("$.activeLoans").value(1))
                .andExpect(jsonPath("$.overdueLoans").value(0))
                .andExpect(jsonPath("$.loanLimit").value(3))
                .andExpect(jsonPath("$.reservationLimit").value(3));
    }

    @Test
    void eachReaderShouldSeeOnlyTheirOwnStateWithEveryReason() throws Exception {
        jdbcTemplate.update("update reader set blocked = true where id = ?", ana);
        returnedLoanWithFine(ana, "3.50");
        returnedLoanWithFine(ana, "6.25");
        var late = activeLoan(ana, 30, -3);
        jdbcTemplate.update("insert into suspension (reader_id, loan_id, start_date, end_date) values (?, ?, ?, ?)",
                ana, late, TODAY.minusDays(1), TODAY.plusDays(4));
        jdbcTemplate.update("insert into reservation (book_id, reader_id, reservation_date, status) "
                + "values (?, ?, ?, 'ACTIVE')", bookId, ana, TODAY);
        activeLoan(bia, 5, 9);

        standing(ana).andExpect(status().isOk())
                .andExpect(jsonPath("$.canBorrow").value(false))
                .andExpect(jsonPath("$.blocked").value(true))
                .andExpect(jsonPath("$.reasons.length()").value(4))
                .andExpect(jsonPath("$.reasons[0]").value("o leitor está bloqueado"))
                .andExpect(jsonPath("$.reasons[1]").value("o leitor tem multa pendente"))
                .andExpect(jsonPath("$.reasons[2]").value("o leitor está suspenso"))
                .andExpect(jsonPath("$.reasons[3]").value("o leitor tem empréstimo em atraso"))
                .andExpect(jsonPath("$.pendingFineTotal").value(9.75))
                .andExpect(jsonPath("$.suspension.startDate").value(TODAY.minusDays(1).toString()))
                .andExpect(jsonPath("$.suspension.releaseDate").value(TODAY.plusDays(4).toString()))
                .andExpect(jsonPath("$.activeLoans").value(1))
                .andExpect(jsonPath("$.overdueLoans").value(1))
                .andExpect(jsonPath("$.activeReservations").value(1));

        // Bia has none of it
        standing(bia).andExpect(status().isOk())
                .andExpect(jsonPath("$.canBorrow").value(true))
                .andExpect(jsonPath("$.blocked").value(false))
                .andExpect(jsonPath("$.pendingFineTotal").value(0))
                .andExpect(jsonPath("$.suspension").doesNotExist())
                .andExpect(jsonPath("$.activeReservations").value(0));
    }

    @Test
    void theStateShouldChangeWithTheDataAndNeverShowTheReasonOfTheBlock() throws Exception {
        returnedLoanWithFine(ana, "3.50");
        var before = standing(ana).andExpect(status().isOk()).andExpect(jsonPath("$.canBorrow").value(false))
                .andReturn().getResponse().getContentAsString();

        jdbcTemplate.update("update fine set status = 'PAID' where reader_id = ?", ana);

        standing(ana).andExpect(status().isOk()).andExpect(jsonPath("$.canBorrow").value(true))
                .andExpect(jsonPath("$.pendingFineTotal").value(0));
        assertThat(before).doesNotContain("waiver").doesNotContain("document").doesNotContain("RST-DOC");
    }

    @Test
    void aReaderOverTheLimitOfLoansShouldBeToldTheLimit() throws Exception {
        jdbcTemplate.update("update library_configuration set loan_limit = 1");
        activeLoan(ana, 2, 12);

        standing(ana).andExpect(status().isOk())
                .andExpect(jsonPath("$.canBorrow").value(false))
                .andExpect(jsonPath("$.loanLimit").value(1))
                .andExpect(jsonPath("$.reasons[0]").value("o leitor já atingiu o limite de 1 empréstimo simultâneo"));
    }

    @Test
    void theReaderOfTheRouteShouldBeTheOneOfTheTokenAndAnyIdInTheRequestIsIgnored() throws Exception {
        returnedLoanWithFine(ana, "3.50");

        mockMvc.perform(get("/api/v1/me/standing").param("readerId", String.valueOf(ana))
                        .header("Authorization", asReader(bia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canBorrow").value(true))
                .andExpect(jsonPath("$.pendingFineTotal").value(0));
    }

    @Test
    void anAdministratorMayNotReadTheStandingOfTheReader() throws Exception {
        var token = "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", "ADMIN", Instant.now(),
                Instant.now().plusSeconds(900));

        mockMvc.perform(get("/api/v1/me/standing").header("Authorization", token)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/me/standing")).andExpect(status().isUnauthorized());
    }

    @Test
    void theQueriesShouldBeFixedWhateverTheReaderOwes() throws Exception {
        // the same kinds of data before and after, so only the volume changes (an empty page skips its count query)
        returnedLoanWithFine(ana, "3.50");
        activeLoan(ana, 5, 9);
        var few = queriesFor(ana);

        for (var i = 0; i < 30; i++) {
            returnedLoanWithFine(ana, "1.00");
        }
        var many = queriesFor(ana);

        assertThat(many).isEqualTo(few).isLessThanOrEqualTo(10);
    }

    private long queriesFor(long readerId) throws Exception {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        standing(readerId).andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }
}
