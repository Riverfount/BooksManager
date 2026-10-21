package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
 * Returning a loan (RF12, RF20, HU08) through the real path: security chain, transaction and
 * PostgreSQL, with no test transaction (a test transaction would hide the rollback and the
 * commit after an exception, and the race never has a second connection). The data is
 * committed, so it is removed after each test and the configuration is restored.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ReturnLoanIntegrationTest {

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
                "insert into category (name) values ('Return loan test') returning id", Long.class);
        bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000057', 'T', ?) returning id",
                Long.class, categoryId);
        readerId = jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', 'RTN-DOC-1', 'r@example.com', date '1990-01-01') returning id
                """, Long.class);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from reservation where reader_id in (select id from reader where document like 'RTN-DOC-%')");
        jdbcTemplate.update("delete from suspension where reader_id in (select id from reader where document like 'RTN-DOC-%')");
        jdbcTemplate.update("delete from fine where reader_id in (select id from reader where document like 'RTN-DOC-%')");
        jdbcTemplate.update("delete from loan where reader_id in (select id from reader where document like 'RTN-DOC-%')");
        jdbcTemplate.update("delete from copy where asset_code like 'RTN-%'");
        jdbcTemplate.update("delete from reader where document like 'RTN-DOC-%'");
        jdbcTemplate.update("delete from book where isbn = '9780000000057'");
        jdbcTemplate.update("delete from category where name = 'Return loan test'");
        jdbcTemplate.update("""
                update library_configuration set penalty_policy = 'FINE', daily_fine_amount = 1.00,
                    fine_cap = 10.00, suspension_days_per_day = 1, suspension_cap_days = 365,
                    loan_period_days = 14, loan_limit = 3, renewal_limit = 2, reservation_pickup_days = 3
                """);
    }

    private void policyIs(String policy) {
        jdbcTemplate.update("update library_configuration set penalty_policy = ?", policy);
    }

    /** An active loan picked up {@code daysAgo} days ago for 14 days, with its copy borrowed. */
    private long activeLoan(int daysAgo, String copyStatus) {
        var copyId = jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, ?, ?) returning id",
                Long.class, bookId, "RTN-" + ++sequence, copyStatus);
        var pickup = TODAY.minusDays(daysAgo);
        return jdbcTemplate.queryForObject("""
                insert into loan (copy_id, reader_id, pickup_date, due_date) values (?, ?, ?, ?) returning id
                """, Long.class, copyId, readerId, pickup, pickup.plusDays(14));
    }

    private long activeLoan(int daysAgo) {
        return activeLoan(daysAgo, "BORROWED");
    }

    private long newReader(String document) {
        return jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', ?, 'r@example.com', date '1990-01-01') returning id
                """, Long.class, document);
    }

    /** A reservation of the book waiting in the queue, made on the given date. */
    private long waitingReservation(long reader, LocalDate reservationDate) {
        return jdbcTemplate.queryForObject("""
                insert into reservation (book_id, reader_id, reservation_date, status) values (?, ?, ?, 'ACTIVE')
                returning id
                """, Long.class, bookId, reader, reservationDate);
    }

    private Map<String, Object> reservationRow(long id) {
        return jdbcTemplate.queryForMap(
                "select status, copy_id, pickup_deadline from reservation where id = ?", id);
    }

    private String token() {
        return "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", "OPERATOR", Instant.now(),
                Instant.now().plusSeconds(900));
    }

    private ResultActions giveBack(long loanId) throws Exception {
        return mockMvc.perform(post("/api/v1/loans/" + loanId + "/return").header("Authorization", token()));
    }

    private String copyStatusOf(long loanId) {
        return jdbcTemplate.queryForObject(
                "select c.status from copy c join loan l on l.copy_id = c.id where l.id = ?", String.class, loanId);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table + " where reader_id = ?", Integer.class,
                readerId);
    }

    // the condition of the copy (RF15)

    private ResultActions giveBackAs(long loanId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/loans/" + loanId + "/return").header("Authorization", token())
                .contentType("application/json").content(body));
    }

    private String conditionOf(long loanId) {
        return jdbcTemplate.queryForObject("select return_condition from loan where id = ?", String.class, loanId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DAMAGED", "LOST"})
    void aCopyReturnedDamagedOrLostShouldCloseTheLoanAndNeverBecomeAvailable(String condition) throws Exception {
        var loanId = activeLoan(5);

        giveBackAs(loanId, "{\"condition\": \"" + condition + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loan.active").value(false))
                .andExpect(jsonPath("$.loan.returnCondition").value(condition));

        assertThat(copyStatusOf(loanId)).isEqualTo(condition);
        assertThat(conditionOf(loanId)).isEqualTo(condition);
    }

    @Test
    void aReturnWithoutBodyShouldStillBeGood() throws Exception {
        var loanId = activeLoan(5);

        giveBack(loanId).andExpect(status().isOk()).andExpect(jsonPath("$.loan.returnCondition").value("GOOD"));

        assertThat(copyStatusOf(loanId)).isEqualTo("AVAILABLE");
        assertThat(conditionOf(loanId)).isEqualTo("GOOD");
    }

    @Test
    void aLateReturnOfALostCopyShouldStillStoreThePenalty() throws Exception {
        policyIs("BOTH");
        var loanId = activeLoan(20);

        giveBackAs(loanId, "{\"condition\": \"LOST\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysLate").value(6))
                .andExpect(jsonPath("$.fine.amount").value(6.00))
                .andExpect(jsonPath("$.suspension.endDate").value(TODAY.plusDays(6).toString()));

        assertThat(copyStatusOf(loanId)).isEqualTo("LOST");
        assertThat(count("fine")).isEqualTo(1);
        assertThat(count("suspension")).isEqualTo(1);
    }

    @Test
    void anUnknownConditionShouldAnswer400AndChangeNothing() throws Exception {
        var loanId = activeLoan(5);

        giveBackAs(loanId, "{\"condition\": \"STOLEN\"}").andExpect(status().isBadRequest());

        assertThat(copyStatusOf(loanId)).isEqualTo("BORROWED");
        assertThat(conditionOf(loanId)).isNull();
    }

    @Test
    void theHistoryShouldShowTheConditionOfTheReturn() throws Exception {
        var loanId = activeLoan(5);
        giveBackAs(loanId, "{\"condition\": \"DAMAGED\"}").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/loans/" + loanId).header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnCondition").value("DAMAGED"));
    }

    // on time and late (HU08)

    @Test
    void anOnTimeReturnShouldCloseTheLoanFreeTheCopyAndApplyNothing() throws Exception {
        policyIs("BOTH");
        var loanId = activeLoan(14);

        giveBack(loanId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysLate").value(0))
                .andExpect(jsonPath("$.loan.active").value(false))
                .andExpect(jsonPath("$.fine").doesNotExist())
                .andExpect(jsonPath("$.suspension").doesNotExist());

        assertThat(copyStatusOf(loanId)).isEqualTo("AVAILABLE");
        assertThat(count("fine")).isZero();
        assertThat(count("suspension")).isZero();
    }

    @Test
    void aLateReturnUnderTheFinePolicyShouldStoreTheFineOnly() throws Exception {
        policyIs("FINE");
        var loanId = activeLoan(20); // 6 days late

        giveBack(loanId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysLate").value(6))
                .andExpect(jsonPath("$.fine.amount").value(6.00))
                .andExpect(jsonPath("$.fine.status").value("PENDING"))
                .andExpect(jsonPath("$.suspension").doesNotExist());

        assertThat(copyStatusOf(loanId)).isEqualTo("AVAILABLE");
        assertThat(count("fine")).isEqualTo(1);
        assertThat(count("suspension")).isZero();
    }

    @Test
    void aLateReturnUnderTheSuspensionPolicyShouldStoreTheSuspensionOnly() throws Exception {
        policyIs("SUSPENSION");

        giveBack(activeLoan(20))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fine").doesNotExist())
                .andExpect(jsonPath("$.suspension.startDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.suspension.endDate").value(TODAY.plusDays(6).toString()));

        assertThat(count("fine")).isZero();
        assertThat(count("suspension")).isEqualTo(1);
    }

    @Test
    void aLateReturnUnderTheBothPolicyShouldStoreBoth() throws Exception {
        policyIs("BOTH");

        giveBack(activeLoan(20))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fine.amount").value(6.00))
                .andExpect(jsonPath("$.suspension.endDate").value(TODAY.plusDays(6).toString()));

        assertThat(count("fine")).isEqualTo(1);
        assertThat(count("suspension")).isEqualTo(1);
    }

    @Test
    void aLateReturnUnderTheNonePolicyShouldStoreNothing() throws Exception {
        policyIs("NONE");

        giveBack(activeLoan(20)).andExpect(status().isOk()).andExpect(jsonPath("$.daysLate").value(6));

        assertThat(count("fine")).isZero();
        assertThat(count("suspension")).isZero();
    }

    @Test
    void aDamagedCopyShouldNotBecomeAvailableByComingBack() throws Exception {
        var loanId = activeLoan(5, "DAMAGED");

        giveBack(loanId).andExpect(status().isOk());

        assertThat(copyStatusOf(loanId)).isEqualTo("DAMAGED");
    }

    // a change of policy does not recalculate what was returned (RN16)

    @Test
    void changingThePolicyShouldNotChangeWhatWasAlreadyReturned() throws Exception {
        policyIs("FINE");
        giveBack(activeLoan(20)).andExpect(status().isOk());
        policyIs("SUSPENSION");

        giveBack(activeLoan(20))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fine").doesNotExist())
                .andExpect(jsonPath("$.suspension").exists());

        assertThat(count("fine")).as("the first return keeps its fine").isEqualTo(1);
        assertThat(count("suspension")).as("only the second return got a suspension").isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select amount from fine where reader_id = ?",
                java.math.BigDecimal.class, readerId)).isEqualByComparingTo("6.00");
    }

    // a closed loan, a loan that does not exist (RN09)

    @Test
    void returningALoanTwiceShouldAnswer422AndApplyThePenaltyOnlyOnce() throws Exception {
        policyIs("BOTH");
        var loanId = activeLoan(20);
        giveBack(loanId).andExpect(status().isOk());

        giveBack(loanId).andExpect(status().isUnprocessableContent());

        assertThat(count("fine")).isEqualTo(1);
        assertThat(count("suspension")).isEqualTo(1);
    }

    @Test
    void aLoanThatDoesNotExistShouldAnswer404() throws Exception {
        giveBack(-1L).andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value(containsString("-1")));
    }

    @Test
    void withoutATokenTheAnswerShouldBe401() throws Exception {
        mockMvc.perform(post("/api/v1/loans/" + activeLoan(5) + "/return")).andExpect(status().isUnauthorized());
    }

    // all or nothing (RNF16) and the race (RNF17)

    @Test
    void aFailureAfterTheFirstStepsShouldUndoEverything() throws Exception {
        policyIs("BOTH");
        // the copy says it is available, but the loan is open: marking it as back fails
        var loanId = activeLoan(20, "AVAILABLE");

        giveBack(loanId).andExpect(status().isUnprocessableContent());

        assertThat(jdbcTemplate.queryForObject("select return_date from loan where id = ?", java.sql.Date.class,
                loanId)).as("the loan stays open").isNull();
        assertThat(count("fine")).isZero();
        assertThat(count("suspension")).isZero();
    }

    @Test
    void twoSimultaneousReturnsOfTheSameLoanShouldLetOnlyOnePass() throws Exception {
        policyIs("BOTH");
        var loanId = activeLoan(20);
        var startTogether = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        var results = new ArrayList<Future<Integer>>();
        try {
            for (var i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    startTogether.await(10, TimeUnit.SECONDS);
                    return giveBack(loanId).andReturn().getResponse().getStatus();
                }));
            }
            var statuses = new ArrayList<Integer>();
            for (var result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses.stream().filter(s -> s == 200).count()).as("statuses %s", statuses).isEqualTo(1);
            assertThat(statuses).allMatch(s -> s == 200 || s == 409 || s == 422);
        } finally {
            pool.shutdownNow();
        }

        assertThat(count("fine")).as("a single fine").isEqualTo(1);
        assertThat(count("suspension")).as("a single suspension").isEqualTo(1);
        assertThat(copyStatusOf(loanId)).isEqualTo("AVAILABLE");
    }

    // the queue of reservations (RF18, RN13)

    @Test
    void returningWithAQueueShouldKeepTheCopyForTheFirstReaderWithThePickupDeadline() throws Exception {
        var first = waitingReservation(newReader("RTN-DOC-2"), TODAY.minusDays(2));
        var second = waitingReservation(newReader("RTN-DOC-3"), TODAY.minusDays(1));
        var loanId = activeLoan(5);

        giveBack(loanId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservation.id").value(first))
                .andExpect(jsonPath("$.reservation.pickupDeadline").value(TODAY.plusDays(3).toString()));

        assertThat(copyStatusOf(loanId)).isEqualTo("RESERVED");
        var row = reservationRow(first);
        assertThat(row.get("copy_id")).isEqualTo(jdbcTemplate.queryForObject(
                "select copy_id from loan where id = ?", Long.class, loanId));
        assertThat(row.get("pickup_deadline").toString()).isEqualTo(TODAY.plusDays(3).toString());
        assertThat(reservationRow(second).get("copy_id")).isNull();
    }

    @Test
    void twoReturnsOfTheSameBookShouldGoToDifferentReadersOfTheQueue() throws Exception {
        var first = waitingReservation(newReader("RTN-DOC-2"), TODAY.minusDays(2));
        var second = waitingReservation(newReader("RTN-DOC-3"), TODAY.minusDays(1));
        var firstLoan = activeLoan(5);
        var secondLoan = activeLoan(6);

        giveBack(firstLoan).andExpect(jsonPath("$.reservation.id").value(first));
        giveBack(secondLoan).andExpect(jsonPath("$.reservation.id").value(second));

        assertThat(reservationRow(first).get("copy_id")).isNotEqualTo(reservationRow(second).get("copy_id"));
        assertThat(copyStatusOf(firstLoan)).isEqualTo("RESERVED");
        assertThat(copyStatusOf(secondLoan)).isEqualTo("RESERVED");
    }

    @Test
    void returningWithoutAQueueShouldLeaveTheCopyAvailable() throws Exception {
        var loanId = activeLoan(5);

        giveBack(loanId).andExpect(status().isOk()).andExpect(jsonPath("$.reservation").doesNotExist());

        assertThat(copyStatusOf(loanId)).isEqualTo("AVAILABLE");
    }

    @Test
    void aDamagedCopyShouldNeverGoToTheQueue() throws Exception {
        var waiting = waitingReservation(newReader("RTN-DOC-2"), TODAY.minusDays(1));
        var loanId = activeLoan(5);

        giveBackAs(loanId, "{\"condition\": \"DAMAGED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservation").doesNotExist());

        assertThat(copyStatusOf(loanId)).isEqualTo("DAMAGED");
        assertThat(reservationRow(waiting).get("copy_id")).isNull();
    }

    @Test
    void aFailureWhileHandingTheCopyOverShouldUndoTheWholeReturn() throws Exception {
        // a reservation dated in the future cannot receive the copy: the domain refuses, after the copy was
        // already checked in and kept, so the whole return must roll back
        var waiting = waitingReservation(newReader("RTN-DOC-2"), TODAY.plusDays(1));
        var loanId = activeLoan(5);

        giveBack(loanId).andExpect(status().isUnprocessableContent());

        assertThat(copyStatusOf(loanId)).isEqualTo("BORROWED");
        assertThat(jdbcTemplate.queryForObject("select return_date from loan where id = ?", Object.class, loanId))
                .isNull();
        assertThat(reservationRow(waiting).get("copy_id")).isNull();
    }
}
