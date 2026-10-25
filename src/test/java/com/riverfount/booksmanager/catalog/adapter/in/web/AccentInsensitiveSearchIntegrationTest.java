package com.riverfount.booksmanager.catalog.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The search of books and of authors without accents (RF05) through the real path: the security chain, the
 * controller, the query with the unaccent extension and PostgreSQL. "jose" finds "José" and "garcia marquez" finds
 * "García Márquez", and the other way round, in the title and in the author of the book, and in the name of the
 * author. The data is committed, so it is removed after each test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class AccentInsensitiveSearchIntegrationTest {

    private static final String MARKER = "Accent search test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    @BeforeEach
    void createTheCatalog() {
        cleanUp();
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values (?) returning id", Long.class, MARKER);
        var marquez = jdbcTemplate.queryForObject(
                "insert into author (name) values (?) returning id", Long.class, MARKER + " Gabriel García Márquez");
        var other = jdbcTemplate.queryForObject(
                "insert into author (name) values (?) returning id", Long.class, MARKER + " Autor Qualquer");
        var first = jdbcTemplate.queryForObject("""
                insert into book (isbn, title, publisher, publication_year, category_id)
                values ('9780000000354', ?, 'Editora', 1967, ?) returning id
                """, Long.class, MARKER + " Cem anos de solidão", categoryId);
        var second = jdbcTemplate.queryForObject("""
                insert into book (isbn, title, publisher, publication_year, category_id)
                values ('9780000000361', ?, 'Editora', 2000, ?) returning id
                """, Long.class, MARKER + " Jose e a Árvore", categoryId);
        jdbcTemplate.update("insert into book_author (book_id, author_id) values (?, ?)", first, marquez);
        jdbcTemplate.update("insert into book_author (book_id, author_id) values (?, ?)", second, other);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from book_author where author_id in (select id from author where name like ?)",
                MARKER + "%");
        jdbcTemplate.update("delete from book where title like ?", MARKER + "%");
        jdbcTemplate.update("delete from author where name like ?", MARKER + "%");
        jdbcTemplate.update("delete from category where name = ?", MARKER);
    }

    private ResultActions search(String path, String param, String value) throws Exception {
        return mockMvc.perform(get(path).param(param, value)
                .header("Authorization", "Bearer " + TestTokens.valid(encoder, properties, "OPERATOR")));
    }

    @Test
    void theTitleWithAccentsShouldBeFoundWithoutTypingThem() throws Exception {
        search("/api/v1/books", "title", "cem anos de solidao").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].title").value(MARKER + " Cem anos de solidão"));
    }

    @Test
    void theTitleWithoutAccentsShouldBeFoundTypingThem() throws Exception {
        search("/api/v1/books", "title", MARKER + " José e a árvore").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].title").value(MARKER + " Jose e a Árvore"));
    }

    @Test
    void theAuthorOfABookShouldBeFoundWithoutTypingTheAccents() throws Exception {
        search("/api/v1/books", "author", "garcia marquez").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].title").value(MARKER + " Cem anos de solidão"));
    }

    @Test
    void theNameOfTheAuthorShouldBeFoundWithoutTypingTheAccents() throws Exception {
        search("/api/v1/authors", "name", MARKER + " gabriel garcia").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].name").value(MARKER + " Gabriel García Márquez"));
    }

    @Test
    void aNameThatDiffersByAnotherLetterShouldNotBeFound() throws Exception {
        search("/api/v1/authors", "name", MARKER + " gabriel garsia").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
    }
}
