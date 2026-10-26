package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.hibernate.SessionFactory;
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
 * The loans of the reader (Phase 6) through the real path: security chain, transactions and PostgreSQL, with no test
 * transaction (it would hide the commit and the rollback). The point is the owner of the data: with two readers,
 * nobody reaches, renews or learns anything about the loan of the other; and the renewal keeps every rule of the desk.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ReaderLoanIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    private static final int QUERIES_PER_PAGE = 7;

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
        jdbcTemplate.update("update library_configuration set renewal_limit = 2, loan_period_days = 14");
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('RLN test') returning id", Long.class);
        bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000007155', 'RLN Dom Casmurro', ?) returning id",
                Long.class, categoryId);
        ana = newReader("Ana");
        bia = newReader("Bia");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from reservation where reader_id in (select id from reader where document like 'RLN-DOC-%')");
        jdbcTemplate.update("delete from fine where reader_id in (select id from reader where document like 'RLN-DOC-%')");
        jdbcTemplate.update("delete from loan where reader_id in (select id from reader where document like 'RLN-DOC-%')");
        jdbcTemplate.update("delete from copy where asset_code like 'RLN-%'");
        jdbcTemplate.update("delete from reader where document like 'RLN-DOC-%'");
        jdbcTemplate.update("delete from book where isbn = '9780000007155'");
        jdbcTemplate.update("delete from category where name = 'RLN test'");
    }

    private long newReader(String name) {
        return jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values (?, ?, 'r@example.com', date '1990-01-01') returning id
                """, Long.class, name, "RLN-DOC-" + ++sequence);
    }

    private long newCopy(String status) {
        return jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, ?, ?) returning id", Long.class, bookId,
                "RLN-" + ++sequence, status);
    }

    /** An active loan of the reader, picked up {@code daysAgo} days ago and due in {@code dueInDays} days. */
    private long activeLoan(long readerId, int daysAgo, int dueInDays, int renewals) {
        return jdbcTemplate.queryForObject("""
                insert into loan (copy_id, reader_id, pickup_date, due_date, renewals) values (?, ?, ?, ?, ?) returning id
                """, Long.class, newCopy("BORROWED"), readerId, TODAY.minusDays(daysAgo), TODAY.plusDays(dueInDays),
                renewals);
    }

    private long returnedLoan(long readerId) {
        return jdbcTemplate.queryForObject("""
                insert into loan (copy_id, reader_id, pickup_date, due_date, return_date, return_condition)
                values (?, ?, ?, ?, ?, 'GOOD') returning id
                """, Long.class, newCopy("AVAILABLE"), readerId, TODAY.minusDays(30), TODAY.minusDays(16),
                TODAY.minusDays(17));
    }

    private String asReader(long readerId) {
        return "Bearer " + TestTokens.forReader(encoder, properties, 1000 + readerId, readerId);
    }

    private ResultActions renew(long readerId, long loanId) throws Exception {
        return mockMvc.perform(post("/api/v1/me/loans/" + loanId + "/renew").header("Authorization", asReader(readerId)));
    }

    private ResultActions find(long readerId, long loanId) throws Exception {
        return mockMvc.perform(get("/api/v1/me/loans/" + loanId).header("Authorization", asReader(readerId)));
    }

    private LocalDate dueDateOf(long loanId) {
        return jdbcTemplate.queryForObject("select due_date from loan where id = ?", LocalDate.class, loanId);
    }

    private int renewalsOf(long loanId) {
        return jdbcTemplate.queryForObject("select renewals from loan where id = ?", Integer.class, loanId);
    }

    // looking: only what is theirs

    @Test
    void shouldListOnlyTheLoansOfTheReaderWithTheBookAndNothingAboutTheCopy() throws Exception {
        var anas = activeLoan(ana, 5, 9, 0);
        var anasOld = returnedLoan(ana);
        activeLoan(bia, 5, 9, 0);

        var body = mockMvc.perform(get("/api/v1/me/loans").header("Authorization", asReader(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].bookTitle").value("RLN Dom Casmurro"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"id\":" + anas).contains("\"id\":" + anasOld)
                .doesNotContain("RLN-").doesNotContain("assetCode").doesNotContain("readerId")
                .doesNotContain("readerName").doesNotContain("Bia");
        // a readerId in the query is not a filter of the reader: it is ignored
        mockMvc.perform(get("/api/v1/me/loans?readerId=" + bia).header("Authorization", asReader(ana)))
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void shouldFilterByStatusAndByOverdue() throws Exception {
        activeLoan(ana, 5, 9, 0);
        var late = activeLoan(ana, 30, -3, 0);
        returnedLoan(ana);

        mockMvc.perform(get("/api/v1/me/loans?status=ACTIVE").header("Authorization", asReader(ana)))
                .andExpect(jsonPath("$.totalItems").value(2));
        mockMvc.perform(get("/api/v1/me/loans?status=RETURNED").header("Authorization", asReader(ana)))
                .andExpect(jsonPath("$.totalItems").value(1));
        mockMvc.perform(get("/api/v1/me/loans?overdue=true").header("Authorization", asReader(ana)))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(late))
                .andExpect(jsonPath("$.items[0].daysLate").value(3));
    }

    @Test
    void aReturnedLoanShouldShowItsFineWithoutTheWaiverReason() throws Exception {
        var loan = returnedLoan(ana);
        jdbcTemplate.update("insert into fine (loan_id, reader_id, amount, status, waiver_reason) "
                + "values (?, ?, 6.00, 'WAIVED', 'Internação do leitor')", loan, ana);

        mockMvc.perform(get("/api/v1/me/loans/" + loan).header("Authorization", asReader(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fine.amount").value(6.00))
                .andExpect(jsonPath("$.fine.status").value("WAIVED"))
                .andExpect(content().string(not(containsString("Internação"))));
    }

    @Test
    void theLoanOfAnotherReaderShouldBeA404ExactlyLikeOneThatDoesNotExist() throws Exception {
        var anas = activeLoan(ana, 5, 9, 0);

        var another = find(bia, anas).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        var missing = find(bia, anas + 100000).andExpect(status().isNotFound()).andReturn().getResponse()
                .getContentAsString();

        assertThat(another.replaceAll("\\d+", "N")).isEqualTo(missing.replaceAll("\\d+", "N"));
    }

    @Test
    void theQueriesPerPageShouldBeFixedWhateverThePageSize() throws Exception {
        for (var i = 0; i < 4; i++) {
            activeLoan(ana, 5, 9, 0);
        }
        var small = queriesFor("/api/v1/me/loans?size=2");

        for (var i = 0; i < 50; i++) {
            activeLoan(ana, 5, 9, 0);
        }
        var big = queriesFor("/api/v1/me/loans?size=50");

        assertThat(big).isEqualTo(small).isLessThanOrEqualTo(QUERIES_PER_PAGE);
    }

    private long queriesFor(String path) throws Exception {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(get(path).header("Authorization", asReader(ana)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isNotEmpty());
        return statistics.getPrepareStatementCount();
    }

    // renewing

    @Test
    void shouldRenewTheirOwnLoanByThePeriodOfTheConfigurationAndCountIt() throws Exception {
        var loan = activeLoan(ana, 5, 9, 0);
        var due = dueDateOf(loan);

        renew(ana, loan).andExpect(status().isOk())
                .andExpect(jsonPath("$.renewals").value(1))
                .andExpect(jsonPath("$.dueDate").value(due.plusDays(14).toString()));

        assertThat(dueDateOf(loan)).isEqualTo(due.plusDays(14));
        assertThat(renewalsOf(loan)).isEqualTo(1);
    }

    @Test
    void shouldAcceptNoDateInTheRequest() throws Exception {
        var loan = activeLoan(ana, 5, 9, 0);
        var due = dueDateOf(loan);

        mockMvc.perform(post("/api/v1/me/loans/" + loan + "/renew").header("Authorization", asReader(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dueDate\":\"2099-01-01\"}"))
                .andExpect(status().isOk());

        assertThat(dueDateOf(loan)).isEqualTo(due.plusDays(14));
    }

    @Test
    void theLimitOfRenewalsShouldBeKeptAndTheRefusedRenewalChangesNothing() throws Exception {
        var loan = activeLoan(ana, 5, 9, 0);
        renew(ana, loan).andExpect(status().isOk());
        renew(ana, loan).andExpect(status().isOk());
        var due = dueDateOf(loan);

        renew(ana, loan).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("limite de renovações")));

        assertThat(renewalsOf(loan)).isEqualTo(2);
        assertThat(dueDateOf(loan)).isEqualTo(due);
    }

    @Test
    void aLateLoanShouldNotBeRenewed() throws Exception {
        var loan = activeLoan(ana, 30, -3, 0);

        renew(ana, loan).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("atraso")));

        assertThat(renewalsOf(loan)).isZero();
    }

    @Test
    void aReaderWaitingInTheQueueOfTheBookShouldBlockTheRenewal() throws Exception {
        var loan = activeLoan(ana, 5, 9, 0);
        jdbcTemplate.update("insert into reservation (book_id, reader_id, reservation_date, status) "
                + "values (?, ?, ?, 'ACTIVE')", bookId, bia, TODAY);

        renew(ana, loan).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("reserva esperando na fila")))
                // the message says there is a queue, never who is in it
                .andExpect(content().string(not(containsString("Bia"))));

        assertThat(renewalsOf(loan)).isZero();
    }

    @Test
    void aReturnedLoanOfTheirsShouldNotBeRenewed() throws Exception {
        var loan = returnedLoan(ana);

        renew(ana, loan).andExpect(status().isUnprocessableContent());
    }

    @Test
    void shouldNeverRenewTheLoanOfAnotherReader() throws Exception {
        var anas = activeLoan(ana, 5, 9, 0);
        var due = dueDateOf(anas);

        renew(bia, anas).andExpect(status().isNotFound());

        assertThat(dueDateOf(anas)).isEqualTo(due);
        assertThat(renewalsOf(anas)).isZero();
    }

    @Test
    void theReturnedLoanOfAnotherReaderIsA404NotA422() throws Exception {
        var anas = returnedLoan(ana);

        // "already returned" would tell Bia the state of Ana's loan
        renew(bia, anas).andExpect(status().isNotFound());
        find(bia, anas).andExpect(status().isNotFound());
    }

    @Test
    void simultaneousRenewalsShouldNeverGoPastTheLimit() throws Exception {
        var loan = activeLoan(ana, 5, 9, 0);
        var due = dueDateOf(loan);
        var barrier = new CyclicBarrier(4);
        var pool = Executors.newFixedThreadPool(4);
        var statuses = new ArrayList<Integer>();
        try {
            Callable<Integer> attempt = () -> {
                barrier.await();
                return renew(ana, loan).andReturn().getResponse().getStatus();
            };
            for (var result : pool.invokeAll(List.of(attempt, attempt, attempt, attempt))) {
                statuses.add(result.get());
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(statuses).allMatch(code -> code == 200 || code == 409 || code == 422);
        assertThat(renewalsOf(loan)).isBetween(1, 2);
        assertThat(dueDateOf(loan)).isEqualTo(due.plusDays(14L * renewalsOf(loan)));
        assertThat(statuses.stream().filter(code -> code == 200).count()).isEqualTo(renewalsOf(loan));
    }

    // who may call

    @Test
    void theAdministrativeProfilesMayNotUseTheLoansOfTheReader() throws Exception {
        var loan = activeLoan(ana, 5, 9, 0);
        for (var role : new String[] {"ADMIN", "OPERATOR"}) {
            var token = "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", role, Instant.now(),
                    Instant.now().plusSeconds(900));
            mockMvc.perform(get("/api/v1/me/loans").header("Authorization", token)).andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/me/loans/" + loan + "/renew").header("Authorization", token))
                    .andExpect(status().isForbidden());
        }
        assertThat(renewalsOf(loan)).isZero();
    }

    @Test
    void theReaderMayNotUseTheAdministrativeLoanRoutes() throws Exception {
        var loan = activeLoan(ana, 5, 9, 0);

        mockMvc.perform(get("/api/v1/loans").header("Authorization", asReader(ana))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/loans/" + loan + "/renew").header("Authorization", asReader(ana)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/loans/" + loan + "/return").header("Authorization", asReader(ana))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        assertThat(renewalsOf(loan)).isZero();
    }
}
