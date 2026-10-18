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
 * Querying fines and suspensions (RF22, HU11, RNF14) with the real security chain, clock and
 * PostgreSQL: the situation of a reader to decide a new loan. Reads only, so each test runs in
 * a transaction that is rolled back.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class FineAndSuspensionQueryIntegrationTest {

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
    private long otherReaderId;
    private int sequence;

    @BeforeEach
    void createReaders() {
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Fine query test') returning id", Long.class);
        bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000101', 'T', ?) returning id",
                Long.class, categoryId);
        readerId = newReader("FQ-DOC-1");
        otherReaderId = newReader("FQ-DOC-2");
    }

    private long newReader(String document) {
        return jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', ?, 'r@example.com', date '1990-01-01') returning id
                """, Long.class, document);
    }

    /** A returned loan to hang a fine or a suspension on. */
    private long returnedLoan(long reader) {
        var copyId = jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, ?, 'AVAILABLE') returning id",
                Long.class, bookId, "FQ-" + ++sequence);
        return jdbcTemplate.queryForObject("""
                insert into loan (copy_id, reader_id, pickup_date, due_date, return_date, return_condition)
                values (?, ?, date '2026-01-01', date '2026-01-15', date '2026-01-21', 'GOOD') returning id
                """, Long.class, copyId, reader);
    }

    private long fine(long reader, String amount, String status, String waiverReason) {
        return jdbcTemplate.queryForObject("""
                insert into fine (loan_id, reader_id, amount, status, waiver_reason)
                values (?, ?, ?::numeric, ?, ?) returning id
                """, Long.class, returnedLoan(reader), reader, amount, status, waiverReason);
    }

    private long suspension(long reader, LocalDate start, LocalDate end, LocalDate endedEarlyOn) {
        return jdbcTemplate.queryForObject("""
                insert into suspension (reader_id, loan_id, start_date, end_date, ended_early_on, ended_early_reason)
                values (?, ?, ?, ?, ?, ?) returning id
                """, Long.class, reader, returnedLoan(reader), start, end, endedEarlyOn,
                endedEarlyOn == null ? null : "Pardoned");
    }

    private ResultActions getAs(String url) throws Exception {
        var token = "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", "OPERATOR", Instant.now(),
                Instant.now().plusSeconds(900));
        return mockMvc.perform(get(url).header("Authorization", token));
    }

    // fines

    @Test
    void theFinesOfAReaderShouldShowWhatIsStillPending() throws Exception {
        var pending = fine(readerId, "6.00", "PENDING", null);
        var paid = fine(readerId, "4.00", "PAID", null);
        fine(otherReaderId, "9.00", "PENDING", null);

        getAs("/api/v1/fines?readerId=" + readerId + "&sort=id,asc")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].id").value(pending))
                .andExpect(jsonPath("$.items[0].pendingAmount").value(6.00))
                .andExpect(jsonPath("$.items[1].id").value(paid))
                .andExpect(jsonPath("$.items[1].pendingAmount").value(0.00));
    }

    @Test
    void shouldFilterTheFinesByStatus() throws Exception {
        var pending = fine(readerId, "6.00", "PENDING", null);
        fine(readerId, "4.00", "PAID", null);
        var waived = fine(readerId, "2.00", "WAIVED", "Hospital stay");

        getAs("/api/v1/fines?readerId=" + readerId + "&status=PENDING")
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(pending));
        getAs("/api/v1/fines?readerId=" + readerId + "&status=WAIVED")
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(waived))
                .andExpect(jsonPath("$.items[0].waiverReason").value("Hospital stay"));
    }

    @Test
    void theFinesShouldBePaginatedWithAtMost100Items() throws Exception {
        for (var i = 0; i < 3; i++) {
            fine(readerId, "1.00", "PENDING", null);
        }

        getAs("/api/v1/fines?readerId=" + readerId + "&size=2&page=1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
        getAs("/api/v1/fines?size=101").andExpect(status().isBadRequest());
        getAs("/api/v1/fines?size=100").andExpect(status().isOk());
    }

    // suspensions

    @Test
    void aSuspensionThatAlreadyEndedShouldNotAppearAsInForce() throws Exception {
        suspension(readerId, TODAY.minusDays(10), TODAY.minusDays(7), null);
        var running = suspension(readerId, TODAY.minusDays(1), TODAY.plusDays(4), null);

        getAs("/api/v1/suspensions?readerId=" + readerId + "&inForce=true")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(running))
                .andExpect(jsonPath("$.items[0].inForce").value(true))
                .andExpect(jsonPath("$.items[0].endDate").value(TODAY.plusDays(4).toString()))
                .andExpect(jsonPath("$.items[0].releaseDate").value(TODAY.plusDays(4).toString()));
    }

    @Test
    void theEndDateAndTheLastDayShouldFollowTheDomainRule() throws Exception {
        var lastDay = suspension(readerId, TODAY.minusDays(2), TODAY.plusDays(1), null);      // today is the last day
        suspension(readerId, TODAY.minusDays(3), TODAY, null);                               // free again today

        getAs("/api/v1/suspensions?readerId=" + readerId + "&inForce=true")
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(lastDay));
    }

    @Test
    void aSuspensionEndedEarlyShouldNotBeInForceAndShowTheReleaseDate() throws Exception {
        var early = suspension(readerId, TODAY.minusDays(5), TODAY.plusDays(20), TODAY.minusDays(1));

        getAs("/api/v1/suspensions?readerId=" + readerId + "&inForce=true")
                .andExpect(jsonPath("$.totalItems").value(0));
        getAs("/api/v1/suspensions?readerId=" + readerId + "&inForce=false")
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(early))
                .andExpect(jsonPath("$.items[0].inForce").value(false))
                .andExpect(jsonPath("$.items[0].endedEarlyOn").value(TODAY.minusDays(1).toString()))
                .andExpect(jsonPath("$.items[0].endedEarlyReason").value("Pardoned"))
                .andExpect(jsonPath("$.items[0].releaseDate").value(TODAY.minusDays(1).toString()))
                .andExpect(jsonPath("$.items[0].endDate").value(TODAY.plusDays(20).toString()));
    }

    @Test
    void withoutTheFilterAllTheSuspensionsOfTheReaderShouldCome() throws Exception {
        suspension(readerId, TODAY.minusDays(10), TODAY.minusDays(7), null);
        suspension(readerId, TODAY.minusDays(1), TODAY.plusDays(4), null);
        suspension(otherReaderId, TODAY.minusDays(1), TODAY.plusDays(4), null);

        getAs("/api/v1/suspensions?readerId=" + readerId)
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].inForce").value(true))
                .andExpect(jsonPath("$.items[1].inForce").value(false));
    }

    @Test
    void theSuspensionsShouldBePaginatedWithAtMost100Items() throws Exception {
        getAs("/api/v1/suspensions?size=101").andExpect(status().isBadRequest());
        getAs("/api/v1/suspensions?size=100").andExpect(status().isOk());
    }

    @Test
    void withoutATokenTheAnswerShouldBe401() throws Exception {
        mockMvc.perform(get("/api/v1/fines")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/suspensions")).andExpect(status().isUnauthorized());
    }
}
