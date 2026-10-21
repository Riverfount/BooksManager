package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import java.time.Instant;
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
 * Reserving a book (RF16) through the real path: the security chain, the transaction and PostgreSQL.
 * There is no test transaction (it would hide the commit and the rollback), so the data is cleaned
 * after each test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ReservationIntegrationTest {

    private static final String URL = "/api/v1/reservations";

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
    void createParents() {
        cleanUp();
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Reservation integration test') returning id", Long.class);
        bookId = newBook("9780000000200", categoryId);
        // the only copy of the book is out on loan, so the book has nothing to lend and can be reserved
        newCopy(bookId, "RI-1", "BORROWED");
        readerId = newReader("RI-DOC-1");
        otherReaderId = newReader("RI-DOC-2");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from reservation where reader_id in (select id from reader where document like 'RI-DOC-%')");
        jdbcTemplate.update("delete from copy where asset_code like 'RI-%'");
        jdbcTemplate.update("delete from book where isbn like '97800000002%'");
        jdbcTemplate.update("delete from category where name = 'Reservation integration test'");
        jdbcTemplate.update("delete from reader where document like 'RI-DOC-%'");
    }

    private long newBook(String isbn, long categoryId) {
        return jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values (?, 'T', ?) returning id", Long.class, isbn,
                categoryId);
    }

    private void newCopy(long book, String assetCode, String status) {
        jdbcTemplate.update("insert into copy (book_id, asset_code, status) values (?, ?, ?)", book, assetCode,
                status);
    }

    private long newReader(String document) {
        return jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', ?, 'r@example.com', date '1990-01-01') returning id
                """, Long.class, document);
    }

    private String token(String role) {
        return "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", role, Instant.now(),
                Instant.now().plusSeconds(900));
    }

    private ResultActions reserve(String role, long book, long reader) throws Exception {
        return mockMvc.perform(post(URL).header("Authorization", token(role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + book + ",\"readerId\":" + reader + "}"));
    }

    private int reservationsOfTheBook() {
        return jdbcTemplate.queryForObject("select count(*) from reservation where book_id = ?", Integer.class,
                bookId);
    }

    @Test
    void anOperatorShouldReserveTheBookAndTakeTheFirstPlaceInTheQueue() throws Exception {
        reserve("OPERATOR", bookId, readerId)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(URL + "/")))
                .andExpect(jsonPath("$.bookId").value(bookId))
                .andExpect(jsonPath("$.readerId").value(readerId))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.queuePosition").value(1))
                .andExpect(jsonPath("$.copyId").doesNotExist());

        var row = jdbcTemplate.queryForMap(
                "select status, copy_id, pickup_deadline, reservation_date from reservation where book_id = ?",
                bookId);
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("copy_id")).isNull();
        assertThat(row.get("pickup_deadline")).isNull();
        assertThat(row.get("reservation_date")).isNotNull();
    }

    @Test
    void anAdministratorShouldAlsoReserve() throws Exception {
        reserve("ADMIN", bookId, readerId).andExpect(status().isCreated());
    }

    @Test
    void theNextReaderShouldGoToTheEndOfTheQueue() throws Exception {
        reserve("OPERATOR", bookId, readerId).andExpect(jsonPath("$.queuePosition").value(1));

        reserve("OPERATOR", bookId, otherReaderId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.queuePosition").value(2));
    }

    @Test
    void theSameBookTwiceByTheSameReaderShouldBeRefusedWith422AndLeaveOneReservation() throws Exception {
        reserve("OPERATOR", bookId, readerId).andExpect(status().isCreated());

        reserve("OPERATOR", bookId, readerId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("reserva ativa deste livro")));

        assertThat(reservationsOfTheBook()).isEqualTo(1);
    }

    @Test
    void aBookWithAnAvailableCopyShouldBeRefusedWith422() throws Exception {
        newCopy(bookId, "RI-2", "AVAILABLE");

        reserve("OPERATOR", bookId, readerId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("exemplar disponível")));

        assertThat(reservationsOfTheBook()).isZero();
    }

    @Test
    void aReaderAtTheLimitOfActiveReservationsShouldBeRefusedWith422() throws Exception {
        var categoryId = jdbcTemplate.queryForObject("select id from category where name = 'Reservation integration test'",
                Long.class);
        for (var i = 1; i <= 3; i++) {
            var other = newBook("978000000020" + i, categoryId);
            jdbcTemplate.update("""
                    insert into reservation (book_id, reader_id, status, reservation_date)
                    values (?, ?, 'ACTIVE', current_date)
                    """, other, readerId);
        }

        reserve("OPERATOR", bookId, readerId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("limite de 3 reservas ativas")));
    }

    @Test
    void theLimitShouldFollowTheConfiguration() throws Exception {
        jdbcTemplate.update("update library_configuration set loan_limit = 1");
        try {
            var categoryId = jdbcTemplate.queryForObject(
                    "select id from category where name = 'Reservation integration test'", Long.class);
            var other = newBook("9780000000209", categoryId);
            jdbcTemplate.update("""
                    insert into reservation (book_id, reader_id, status, reservation_date)
                    values (?, ?, 'ACTIVE', current_date)
                    """, other, readerId);

            reserve("OPERATOR", bookId, readerId)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.detail").value(containsString("limite de 1 reserva ativa")));
        } finally {
            jdbcTemplate.update("update library_configuration set loan_limit = 3");
        }
    }

    @Test
    void severalReasonsShouldComeTogetherInOne422() throws Exception {
        newCopy(bookId, "RI-2", "AVAILABLE");
        jdbcTemplate.update("update reader set blocked = true where id = ?", readerId);

        reserve("OPERATOR", bookId, readerId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("o leitor está bloqueado")))
                .andExpect(jsonPath("$.detail").value(containsString("exemplar disponível")));

        assertThat(reservationsOfTheBook()).isZero();
    }

    @Test
    void anUnknownBookShouldAnswer404() throws Exception {
        reserve("OPERATOR", -1L, readerId).andExpect(status().isNotFound());
    }

    @Test
    void anUnknownReaderShouldAnswer404() throws Exception {
        reserve("OPERATOR", bookId, -1L).andExpect(status().isNotFound());

        assertThat(reservationsOfTheBook()).isZero();
    }

    @Test
    void aMissingBookShouldAnswer400() throws Exception {
        mockMvc.perform(post(URL).header("Authorization", token("OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"readerId\":" + readerId + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withoutATokenTheAnswerShouldBe401() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + bookId + ",\"readerId\":" + readerId + "}"))
                .andExpect(status().isUnauthorized());
    }

    // queries (RF16)

    private long idOf(ResultActions created) throws Exception {
        var location = created.andReturn().getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    @Test
    void shouldFindAReservationByIdWithItsPlaceInTheQueueAndTheNames() throws Exception {
        reserve("OPERATOR", bookId, readerId);
        var second = idOf(reserve("OPERATOR", bookId, otherReaderId));

        mockMvc.perform(get(URL + "/" + second).header("Authorization", token("OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(second))
                .andExpect(jsonPath("$.bookTitle").value("T"))
                .andExpect(jsonPath("$.readerName").value("Reader"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.queuePosition").value(2));
    }

    @Test
    void shouldListTheQueueOfABookInTheOrderOfArrival() throws Exception {
        var first = idOf(reserve("OPERATOR", bookId, readerId));
        var second = idOf(reserve("ADMIN", bookId, otherReaderId));

        mockMvc.perform(get(URL).param("bookId", String.valueOf(bookId)).param("status", "ACTIVE")
                        .param("sort", "id,asc").header("Authorization", token("OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].id").value(first))
                .andExpect(jsonPath("$.items[0].queuePosition").value(1))
                .andExpect(jsonPath("$.items[1].id").value(second))
                .andExpect(jsonPath("$.items[1].queuePosition").value(2));
    }

    @Test
    void aClosedReservationShouldStillBeFoundAndHaveNoPlaceInTheQueue() throws Exception {
        var id = idOf(reserve("OPERATOR", bookId, readerId));
        jdbcTemplate.update("update reservation set status = 'CANCELLED' where id = ?", id);

        mockMvc.perform(get(URL + "/" + id).header("Authorization", token("OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.queuePosition").doesNotExist());
        mockMvc.perform(get(URL).param("readerId", String.valueOf(readerId)).param("status", "ACTIVE")
                        .header("Authorization", token("OPERATOR")))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void aSizeAbove100ShouldAnswer400() throws Exception {
        mockMvc.perform(get(URL).param("size", "101").header("Authorization", token("OPERATOR")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownReservationShouldAnswer404() throws Exception {
        mockMvc.perform(get(URL + "/-1").header("Authorization", token("OPERATOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void queriesWithoutATokenShouldAnswer401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL + "/1")).andExpect(status().isUnauthorized());
    }
}
