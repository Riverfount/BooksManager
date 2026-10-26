package com.riverfount.booksmanager.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
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
 * The catalog for the reader (Phase 6) through the real path: security chain, the search of the catalog and
 * PostgreSQL, with no test transaction. Covers what the issue promises: the reader searches and sees the
 * availability, nothing about the copies themselves ever shows up, an inactive book is not there, and the page costs
 * a fixed number of queries however many books it has.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ReaderCatalogIntegrationTest {

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

    private long categoryId;
    private long authorId;
    private int booksCreated;

    @BeforeEach
    void setUp() {
        cleanUp();
        categoryId = insert("insert into category (name, active) values ('RCAT Ficção', true) returning id");
        authorId = insert("insert into author (name, active) values ('RCAT José de Alencar', true) returning id");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from copy where asset_code like 'RCAT-%'");
        jdbcTemplate.update("delete from book_author where book_id in (select id from book where title like 'RCAT %')");
        jdbcTemplate.update("delete from book where title like 'RCAT %'");
        jdbcTemplate.update("delete from author where name like 'RCAT %'");
        jdbcTemplate.update("delete from category where name like 'RCAT %'");
    }

    private long insert(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    private String reader() {
        return "Bearer " + TestTokens.forReader(encoder, properties, 5L, 42L);
    }

    private String system(String role) {
        return "Bearer " + TestTokens.signed(encoder, properties.issuer(), "7", role, Instant.now(),
                Instant.now().plusSeconds(900));
    }

    private ResultActions asReader(String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", reader()));
    }

    /** A valid ISBN-13 (978 + 9 digits + check digit), since the domain validates the ISBN. */
    private static String isbn13(int number) {
        var body = String.format("978%09d", number + 700);
        var sum = 0;
        for (var i = 0; i < body.length(); i++) {
            sum += (body.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return body + ((10 - sum % 10) % 10);
    }

    /** A book of the author, with: one available copy, one borrowed copy and one available copy that is inactive. */
    private long book(String title, boolean active) {
        var number = booksCreated++;
        var id = insert("insert into book (isbn, title, publisher, publication_year, category_id, active) values ('"
                + isbn13(number) + "', '" + title + "', 'Editora', 1900, " + categoryId + ", " + active
                + ") returning id");
        jdbcTemplate.update("insert into book_author (book_id, author_id) values (?, ?)", id, authorId);
        jdbcTemplate.update("insert into copy (book_id, asset_code, status, active, version) values "
                + "(?, ?, 'AVAILABLE', true, 0), (?, ?, 'BORROWED', true, 0), (?, ?, 'AVAILABLE', false, 0)",
                id, "RCAT-" + number + "-A", id, "RCAT-" + number + "-B", id, "RCAT-" + number + "-C");
        return id;
    }

    @Test
    void shouldSearchTheBooksAndShowOnlyTheCopiesAvailableNow() throws Exception {
        book("RCAT Iracema", true);

        asReader("/api/v1/me/books?title=iracema")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].title").value("RCAT Iracema"))
                // one available and active copy; the borrowed and the inactive ones do not count
                .andExpect(jsonPath("$.items[0].availableCopies").value(1))
                .andExpect(jsonPath("$.items[0].category.name").value("RCAT Ficção"))
                .andExpect(jsonPath("$.items[0].authors[0].name").value("RCAT José de Alencar"));
    }

    @Test
    void nothingAboutTheCopiesThemselvesShouldEverShowUp() throws Exception {
        var id = book("RCAT Iracema", true);

        for (var path : new String[] {"/api/v1/me/books?title=iracema", "/api/v1/me/books/" + id}) {
            asReader(path).andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("RCAT-"))))
                    .andExpect(content().string(not(containsString("assetCode"))))
                    .andExpect(content().string(not(containsString("totalCopies"))))
                    .andExpect(content().string(not(containsString("BORROWED"))))
                    .andExpect(content().string(not(containsString("\"active\""))));
        }
    }

    @Test
    void anInactiveBookShouldNotBeListedOrFoundAndTheReaderCannotAskForIt() throws Exception {
        book("RCAT Ativo", true);
        var inactive = book("RCAT Inativo", false);

        asReader("/api/v1/me/books?title=rcat").andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].title").value("RCAT Ativo"));
        asReader("/api/v1/me/books?title=rcat&active=false").andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].title").value("RCAT Ativo"));
        asReader("/api/v1/me/books/" + inactive).andExpect(status().isNotFound());
        asReader("/api/v1/me/books/999999999").andExpect(status().isNotFound());
    }

    @Test
    void theSearchShouldIgnoreAccentsAndFilterByAuthorAndCategory() throws Exception {
        book("RCAT Iracema", true);

        asReader("/api/v1/me/books?author=jose").andExpect(jsonPath("$.totalItems").value(1));
        asReader("/api/v1/me/books?author=jose&categoryId=" + categoryId).andExpect(jsonPath("$.totalItems").value(1));
        asReader("/api/v1/me/books?categoryId=" + (categoryId + 1000)).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void shouldListOnlyTheActiveAuthorsAndCategories() throws Exception {
        jdbcTemplate.update("insert into author (name, active) values ('RCAT Autor Inativo', false)");
        jdbcTemplate.update("insert into category (name, active) values ('RCAT Categoria Inativa', false)");

        asReader("/api/v1/me/authors?name=rcat").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].name").value("RCAT José de Alencar"))
                .andExpect(content().string(not(containsString("Inativ"))));
        asReader("/api/v1/me/categories?name=rcat").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].name").value("RCAT Ficção"))
                .andExpect(content().string(not(containsString("Inativ"))));
    }

    @Test
    void theAdministrativeProfilesAndAnonymousShouldNotUseTheReaderRoutes() throws Exception {
        book("RCAT Iracema", true);

        for (var path : new String[] {"/api/v1/me/books", "/api/v1/me/books/1", "/api/v1/me/authors",
                "/api/v1/me/categories"}) {
            for (var role : new String[] {"ADMIN", "OPERATOR"}) {
                mockMvc.perform(get(path).header("Authorization", system(role))).andExpect(status().isForbidden());
            }
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void theReaderShouldNotUseTheAdministrativeCatalogRoutes() throws Exception {
        var id = book("RCAT Iracema", true);

        // the administrative listing shows the copy counts and the inactive books: not for the reader
        for (var path : new String[] {"/api/v1/books", "/api/v1/books/" + id, "/api/v1/copies", "/api/v1/authors"}) {
            asReader(path).andExpect(status().isForbidden());
        }
    }

    @Test
    void theQueriesPerPageShouldBeFixedWhateverThePageSize() throws Exception {
        for (var i = 0; i < 4; i++) {
            book("RCAT Livro " + i, true);
        }
        var small = queriesFor("/api/v1/me/books?title=rcat&size=2");

        for (var i = 4; i < 54; i++) {
            book("RCAT Livro " + i, true);
        }
        var big = queriesFor("/api/v1/me/books?title=rcat&size=50");

        assertThat(big).isEqualTo(small).isLessThanOrEqualTo(QUERIES_PER_PAGE);
    }

    private long queriesFor(String path) throws Exception {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        asReader(path).andExpect(status().isOk()).andExpect(jsonPath("$.items").isNotEmpty());
        return statistics.getPrepareStatementCount();
    }
}
