package com.riverfount.booksmanager.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
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
 * The ISBN, the publisher and the year of a book (RN01, RF01) through the real path: the security chain, the
 * controller, the service and PostgreSQL, with the commits. The ISBN-10 and the ISBN-13 of the same book are one ISBN
 * (it is stored as the ISBN-13), so registering one after the other is refused as a duplicate and the lookup
 * reports the book; a publisher or a year out of the limits is a 400, never a 500.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class BookIsbnIntegrationTest {

    private static final String ISBN10 = "85-333-0227-4";
    private static final String ISBN13 = "9788533302273";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    private long categoryId;
    private long authorId;

    @BeforeEach
    void createTheCategoryAndTheAuthor() {
        cleanUp();
        categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Book isbn test') returning id", Long.class);
        authorId = jdbcTemplate.queryForObject(
                "insert into author (name) values ('Book isbn test author') returning id", Long.class);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from book_author where author_id in (select id from author where name = ?)",
                "Book isbn test author");
        jdbcTemplate.update("delete from book where title like 'Book isbn test%'");
        jdbcTemplate.update("delete from author where name = 'Book isbn test author'");
        jdbcTemplate.update("delete from category where name = 'Book isbn test'");
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request
                .header("Authorization", "Bearer " + TestTokens.valid(encoder, properties, "OPERATOR"))
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON));
    }

    private ResultActions register(String isbn, String publisher, Integer year) throws Exception {
        var body = """
                {"isbn":"%s","title":"Book isbn test","publisher":%s,"publicationYear":%s,
                 "categoryId":%d,"authorIds":[%d]}
                """.formatted(isbn, publisher == null ? "null" : "\"" + publisher + "\"",
                year == null ? "null" : year.toString(), categoryId, authorId);
        return send(post("/api/v1/books").content(body));
    }

    private String storedIsbns() {
        return String.join(",", jdbcTemplate.queryForList("select isbn from book where title like 'Book isbn test%'",
                String.class));
    }

    @Test
    void anIsbn10ShouldBeStoredAsTheIsbn13AndAnswerWithIt() throws Exception {
        register(ISBN10, "Garnier", 1899).andExpect(status().isCreated())
                .andExpect(jsonPath("$.isbn").value(ISBN13));

        assertThat(storedIsbns()).isEqualTo(ISBN13);
    }

    @Test
    void theIsbn13OfABookRegisteredWithTheIsbn10ShouldBeRefusedAsADuplicate() throws Exception {
        register(ISBN10, "Garnier", 1899).andExpect(status().isCreated());

        register(ISBN13, "Garnier", 1899).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString(ISBN13)));

        assertThat(storedIsbns()).isEqualTo(ISBN13);
    }

    @Test
    void theIsbn10OfABookRegisteredWithTheIsbn13ShouldBeRefusedAsADuplicate() throws Exception {
        register(ISBN13, "Garnier", 1899).andExpect(status().isCreated());

        register(ISBN10, "Garnier", 1899).andExpect(status().isUnprocessableEntity());

        assertThat(storedIsbns()).isEqualTo(ISBN13);
    }

    @Test
    void theLookupOfTheIsbn10ShouldReportTheBookRegisteredWithTheIsbn13() throws Exception {
        register(ISBN13, "Garnier", 1899).andExpect(status().isCreated());

        send(get("/api/v1/books/lookup").param("isbn", ISBN10)).andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.isbn").value(ISBN13));
    }

    @Test
    void aPublisherOver255CharactersShouldBeA400AndRegisterNothing() throws Exception {
        register(ISBN13, "e".repeat(256), 1899).andExpect(status().isBadRequest());

        assertThat(storedIsbns()).isEmpty();
    }

    @Test
    void aPublisherOf255CharactersShouldBeAccepted() throws Exception {
        register(ISBN13, "e".repeat(255), 1899).andExpect(status().isCreated());
    }

    @Test
    void aPublicationYearBelow1450ShouldBeA400() throws Exception {
        register(ISBN13, "Garnier", 1449).andExpect(status().isBadRequest());
        register(ISBN13, "Garnier", 0).andExpect(status().isBadRequest());
        register(ISBN13, "Garnier", -5).andExpect(status().isBadRequest());

        assertThat(storedIsbns()).isEmpty();
    }

    @Test
    void aPublicationYearOf1450ShouldBeAccepted() throws Exception {
        register(ISBN13, "Garnier", 1450).andExpect(status().isCreated());
    }

    @Test
    void updatingWithAPublisherOrAYearOutOfTheLimitsShouldBeA400AndChangeNothing() throws Exception {
        register(ISBN13, "Garnier", 1899).andExpect(status().isCreated());
        var id = jdbcTemplate.queryForObject("select id from book where isbn = ?", Long.class, ISBN13);

        send(patch("/api/v1/books/" + id).content("{\"publisher\":\"" + "e".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest());
        send(patch("/api/v1/books/" + id).content("{\"publicationYear\":12}")).andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject("select publisher from book where id = ?", String.class, id))
                .isEqualTo("Garnier");
        assertThat(jdbcTemplate.queryForObject("select publication_year from book where id = ?", Integer.class, id))
                .isEqualTo(1899);
    }

    @Test
    void registeringWithoutPublisherOrYearShouldBeA400AndRegisterNothing() throws Exception {
        register(ISBN13, null, 1899).andExpect(status().isBadRequest());
        register(ISBN13, "   ", 1899).andExpect(status().isBadRequest());
        register(ISBN13, "Garnier", null).andExpect(status().isBadRequest());

        assertThat(storedIsbns()).isEmpty();
    }

    @Test
    void updatingToABlankPublisherShouldBeA400AndChangeNothing() throws Exception {
        register(ISBN13, "Garnier", 1899).andExpect(status().isCreated());
        var id = jdbcTemplate.queryForObject("select id from book where isbn = ?", Long.class, ISBN13);

        send(patch("/api/v1/books/" + id).content("{\"publisher\":\"   \"}")).andExpect(status().isBadRequest());
        send(patch("/api/v1/books/" + id).content("{\"publisher\":\"\"}")).andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject("select publisher from book where id = ?", String.class, id))
                .isEqualTo("Garnier");
    }

    @Test
    void aBookFromBeforeTheRuleWithoutPublisherAndYearShouldStayEditableAndBeCompleted() throws Exception {
        var id = jdbcTemplate.queryForObject("""
                insert into book (isbn, title, category_id) values (?, 'Book isbn test legacy', ?) returning id
                """, Long.class, ISBN13, categoryId);
        jdbcTemplate.update("insert into book_author (book_id, author_id) values (?, ?)", id, authorId);

        send(patch("/api/v1/books/" + id).content("{\"title\":\"Book isbn test legacy 2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publisher").doesNotExist());
        assertThat(jdbcTemplate.queryForObject("select publisher from book where id = ?", String.class, id)).isNull();

        send(patch("/api/v1/books/" + id).content("{\"publisher\":\"Garnier\",\"publicationYear\":1899}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publisher").value("Garnier"))
                .andExpect(jsonPath("$.publicationYear").value(1899));
    }
}
