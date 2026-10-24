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
 * A book that was inactivated (RN14) is neither lent nor reserved, through the real path (security chain, transaction
 * and PostgreSQL, with the commits). Inactivating a book does not touch its copies, so the copy stays available and
 * the refusal comes from the book. What is already under way is not undone: the return of a loan in course works,
 * and the copy that comes back is not kept for a waiting reservation, which could not pick it up. The data is
 * committed, so it is removed after each test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class InactiveBookIntegrationTest {

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

    @BeforeEach
    void createTheBookAndTheReaders() {
        cleanUp();
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Inactive book test') returning id", Long.class);
        bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000316', 'T', ?) returning id",
                Long.class, categoryId);
        readerId = newReader("IBK-DOC-1");
        otherReaderId = newReader("IBK-DOC-2");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from reservation where reader_id in (select id from reader where document like 'IBK-DOC-%')");
        jdbcTemplate.update("delete from loan where reader_id in (select id from reader where document like 'IBK-DOC-%')");
        jdbcTemplate.update("delete from copy where asset_code like 'IBK-%'");
        jdbcTemplate.update("delete from reader where document like 'IBK-DOC-%'");
        jdbcTemplate.update("delete from book where isbn = '9780000000316'");
        jdbcTemplate.update("delete from category where name = 'Inactive book test'");
    }

    private long newReader(String document) {
        return jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', ?, 'r@example.com', date '1990-01-01') returning id
                """, Long.class, document);
    }

    private long newCopy(String assetCode, String status) {
        return jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, ?, ?) returning id", Long.class, bookId,
                assetCode, status);
    }

    private void inactivateTheBook() {
        jdbcTemplate.update("update book set active = false where id = ?", bookId);
    }

    private String token() {
        return "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", "OPERATOR", Instant.now(),
                Instant.now().plusSeconds(900));
    }

    private ResultActions lend(long copy, long reader) throws Exception {
        return mockMvc.perform(post("/api/v1/loans").header("Authorization", token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"copyId\":" + copy + ",\"readerId\":" + reader + "}"));
    }

    private ResultActions reserve(long book, long reader) throws Exception {
        return mockMvc.perform(post("/api/v1/reservations").header("Authorization", token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + book + ",\"readerId\":" + reader + "}"));
    }

    private String copyStatus(long copy) {
        return jdbcTemplate.queryForObject("select status from copy where id = ?", String.class, copy);
    }

    private int count(String table, String column, long value) {
        return jdbcTemplate.queryForObject("select count(*) from " + table + " where " + column + " = ?",
                Integer.class, value);
    }

    @Test
    void aCopyOfAnInactiveBookShouldNotBeLentAndWorksAgainOnceTheBookIsActive() throws Exception {
        var copy = newCopy("IBK-1", "AVAILABLE");
        inactivateTheBook();

        lend(copy, readerId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("o livro está inativo")));

        assertThat(copyStatus(copy)).isEqualTo("AVAILABLE");
        assertThat(count("loan", "copy_id", copy)).isZero();

        jdbcTemplate.update("update book set active = true where id = ?", bookId);

        lend(copy, readerId).andExpect(status().isCreated());
        assertThat(copyStatus(copy)).isEqualTo("BORROWED");
    }

    @Test
    void anInactiveBookShouldNotBeReserved() throws Exception {
        // the only copy is out, so the book would be a case for reserving if it were active
        var copy = newCopy("IBK-1", "BORROWED");
        jdbcTemplate.update("insert into loan (copy_id, reader_id, pickup_date, due_date) values (?, ?, ?, ?)",
                copy, otherReaderId, TODAY.minusDays(2), TODAY.plusDays(12));
        inactivateTheBook();

        reserve(bookId, readerId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("o livro está inativo")));

        assertThat(count("reservation", "book_id", bookId)).isZero();
    }

    @Test
    void theOwnerShouldNotPickUpTheReservedCopyOfAnInactiveBookAndTheReservationStays() throws Exception {
        var copy = newCopy("IBK-1", "RESERVED");
        var reservationId = jdbcTemplate.queryForObject("""
                insert into reservation (book_id, reader_id, reservation_date, status, copy_id, pickup_deadline)
                values (?, ?, ?, 'ACTIVE', ?, ?) returning id
                """, Long.class, bookId, readerId, TODAY.minusDays(2), copy, TODAY.plusDays(2));
        inactivateTheBook();

        lend(copy, readerId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("o livro está inativo")));

        assertThat(copyStatus(copy)).isEqualTo("RESERVED");
        assertThat(jdbcTemplate.queryForObject("select status from reservation where id = ?", String.class,
                reservationId)).isEqualTo("ACTIVE");
        assertThat(count("loan", "copy_id", copy)).isZero();
    }

    @Test
    void theReturnOfALoanInCourseShouldWorkAndTheCopyShouldNotBeKeptForAWaitingReservation() throws Exception {
        var copy = newCopy("IBK-1", "BORROWED");
        var loanId = jdbcTemplate.queryForObject("""
                insert into loan (copy_id, reader_id, pickup_date, due_date) values (?, ?, ?, ?) returning id
                """, Long.class, copy, otherReaderId, TODAY.minusDays(2), TODAY.plusDays(12));
        var waiting = jdbcTemplate.queryForObject("""
                insert into reservation (book_id, reader_id, reservation_date, status)
                values (?, ?, ?, 'ACTIVE') returning id
                """, Long.class, bookId, readerId, TODAY.minusDays(1));
        inactivateTheBook();

        mockMvc.perform(post("/api/v1/loans/" + loanId + "/return").header("Authorization", token()))
                .andExpect(status().isOk());

        assertThat(copyStatus(copy)).isEqualTo("AVAILABLE");
        assertThat(jdbcTemplate.queryForObject("select copy_id from reservation where id = ?", Long.class, waiting))
                .isNull();
    }
}
