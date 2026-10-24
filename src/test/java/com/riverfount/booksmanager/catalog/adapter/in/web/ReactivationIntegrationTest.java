package com.riverfount.booksmanager.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Reactivating a book, an author, a category and a copy (RN14) through the real path: security chain, transaction and
 * PostgreSQL, with the commits. Only the administrator reactivates (the operator inactivates but does not undo it).
 * A book comes back only if its category and authors are active; a copy only if its book is. The data is committed,
 * so it is removed after each test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ReactivationIntegrationTest {

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
    private long bookId;
    private long copyId;

    @BeforeEach
    void createInactiveCatalog() {
        cleanUp();
        categoryId = jdbcTemplate.queryForObject(
                "insert into category (name, active) values ('Reactivation test', false) returning id", Long.class);
        authorId = jdbcTemplate.queryForObject(
                "insert into author (name, active) values ('Reactivation author', false) returning id", Long.class);
        bookId = jdbcTemplate.queryForObject("""
                insert into book (isbn, title, category_id, active) values ('9780000000323', 'Reactivation book', ?, false)
                returning id
                """, Long.class, categoryId);
        jdbcTemplate.update("insert into book_author (book_id, author_id) values (?, ?)", bookId, authorId);
        copyId = jdbcTemplate.queryForObject("""
                insert into copy (book_id, asset_code, status, active) values (?, 'REA-1', 'DAMAGED', false)
                returning id
                """, Long.class, bookId);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from loan where copy_id in (select id from copy where asset_code like 'REA-%')");
        jdbcTemplate.update("delete from copy where asset_code like 'REA-%'");
        jdbcTemplate.update("delete from book_author where author_id in (select id from author where name ilike 'Reactivation%')");
        jdbcTemplate.update("delete from book where isbn = '9780000000323'");
        jdbcTemplate.update("delete from author where name ilike 'Reactivation%'");
        jdbcTemplate.update("delete from category where name like 'Reactivation%'");
        jdbcTemplate.update("delete from reader where document like 'REA-DOC-%'");
    }

    private ResultActions reactivate(String role, String route, long id) throws Exception {
        return mockMvc.perform(post("/api/v1/" + route + "/" + id + "/reactivate")
                .header("Authorization", "Bearer " + TestTokens.valid(encoder, properties, role))
                .header("X-Requested-With", "XMLHttpRequest"));
    }

    private boolean active(String table, long id) {
        return jdbcTemplate.queryForObject("select active from " + table + " where id = ?", Boolean.class, id);
    }

    private void setActive(String table, long id, boolean active) {
        jdbcTemplate.update("update " + table + " set active = ? where id = ?", active, id);
    }

    @Test
    void theOperatorShouldGet403OnEveryReactivationAndNothingChanges() throws Exception {
        reactivate("OPERATOR", "categories", categoryId).andExpect(status().isForbidden());
        reactivate("OPERATOR", "authors", authorId).andExpect(status().isForbidden());
        reactivate("OPERATOR", "books", bookId).andExpect(status().isForbidden());
        reactivate("OPERATOR", "copies", copyId).andExpect(status().isForbidden());

        assertThat(active("category", categoryId)).isFalse();
        assertThat(active("author", authorId)).isFalse();
        assertThat(active("book", bookId)).isFalse();
        assertThat(active("copy", copyId)).isFalse();
    }

    @Test
    void withoutATokenTheAnswerShouldBe401() throws Exception {
        mockMvc.perform(post("/api/v1/books/" + bookId + "/reactivate").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anAdministratorShouldReactivateTheCategoryAndTheAuthor() throws Exception {
        reactivate("ADMIN", "categories", categoryId).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.name").value("Reactivation test"));
        reactivate("ADMIN", "authors", authorId).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        assertThat(active("category", categoryId)).isTrue();
        assertThat(active("author", authorId)).isTrue();
    }

    @Test
    void aBookShouldNotComeBackWhileItsCategoryOrAuthorIsInactiveAndTheMessageNamesThem() throws Exception {
        reactivate("ADMIN", "books", bookId).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("categoria inativa: Reactivation test")))
                .andExpect(jsonPath("$.detail").value(containsString("autor inativo: Reactivation author")));

        assertThat(active("book", bookId)).isFalse();
    }

    @Test
    void aBookShouldComeBackOnceItsCategoryAndAuthorsAreActiveAndShowUpInTheDefaultListing() throws Exception {
        setActive("category", categoryId, true);
        setActive("author", authorId, true);

        reactivate("ADMIN", "books", bookId).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.title").value("Reactivation book"))
                .andExpect(jsonPath("$.category.name").value("Reactivation test"));

        assertThat(active("book", bookId)).isTrue();
        mockMvc.perform(get("/api/v1/books").param("title", "Reactivation book")
                        .header("Authorization", "Bearer " + TestTokens.valid(encoder, properties, "OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void reactivatingTheBookShouldNotReactivateItsCopies() throws Exception {
        setActive("category", categoryId, true);
        setActive("author", authorId, true);

        reactivate("ADMIN", "books", bookId).andExpect(status().isOk());

        assertThat(active("copy", copyId)).isFalse();
    }

    @Test
    void aCopyShouldNotComeBackWhileItsBookIsInactiveAndKeepsItsStatusWhenItDoes() throws Exception {
        reactivate("ADMIN", "copies", copyId).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("o livro está inativo")));
        assertThat(active("copy", copyId)).isFalse();

        setActive("book", bookId, true);

        reactivate("ADMIN", "copies", copyId).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.status").value("DAMAGED"));
        assertThat(active("copy", copyId)).isTrue();
    }

    @Test
    void aReactivatedBookShouldBeLentAgain() throws Exception {
        setActive("category", categoryId, true);
        setActive("author", authorId, true);
        setActive("copy", copyId, true);
        jdbcTemplate.update("update copy set status = 'AVAILABLE' where id = ?", copyId);
        var readerId = jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', 'REA-DOC-1', 'r@example.com', date '1990-01-01') returning id
                """, Long.class);
        var loan = "{\"copyId\":" + copyId + ",\"readerId\":" + readerId + "}";
        var operator = "Bearer " + TestTokens.valid(encoder, properties, "OPERATOR");

        mockMvc.perform(post("/api/v1/loans").header("Authorization", operator)
                        .contentType(MediaType.APPLICATION_JSON).content(loan))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("o livro está inativo")));

        reactivate("ADMIN", "books", bookId).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/loans").header("Authorization", operator)
                        .contentType(MediaType.APPLICATION_JSON).content(loan))
                .andExpect(status().isCreated());
    }

    @Test
    void reactivatingSomethingAlreadyActiveShouldAnswer200AndChangeNothing() throws Exception {
        setActive("category", categoryId, true);

        reactivate("ADMIN", "categories", categoryId).andExpect(status().isOk());
        reactivate("ADMIN", "categories", categoryId).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void anUnknownIdShouldAnswer404ForEveryResource() throws Exception {
        reactivate("ADMIN", "categories", 999_999L).andExpect(status().isNotFound());
        reactivate("ADMIN", "authors", 999_999L).andExpect(status().isNotFound());
        reactivate("ADMIN", "books", 999_999L).andExpect(status().isNotFound());
        reactivate("ADMIN", "copies", 999_999L).andExpect(status().isNotFound());
    }

    // reactivating an author whose name another active author has (RN19)

    private ResultActions reactivateAuthor(long id, String body) throws Exception {
        var request = post("/api/v1/authors/" + id + "/reactivate")
                .header("Authorization", "Bearer " + TestTokens.valid(encoder, properties, "ADMIN"))
                .header("X-Requested-With", "XMLHttpRequest");
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    @Test
    void reactivatingAnAuthorWhoseNameAnotherActiveAuthorHasShouldBe422WithTheExistingIdUnlessConfirmed()
            throws Exception {
        var otherId = jdbcTemplate.queryForObject(
                "insert into author (name) values ('REACTIVATION AUTHOR') returning id", Long.class);

        reactivateAuthor(authorId, null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.existingAuthorId").value(otherId))
                .andExpect(jsonPath("$.detail").value(containsString("allowDuplicate")));
        reactivateAuthor(authorId, "{\"allowDuplicate\":false}").andExpect(status().isUnprocessableContent());
        assertThat(active("author", authorId)).isFalse();

        reactivateAuthor(authorId, "{\"allowDuplicate\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        assertThat(active("author", authorId)).isTrue();
    }

    @Test
    void anInactiveAuthorWithTheSameNameShouldNotBlockAndAnActiveOneIsNotChecked() throws Exception {
        jdbcTemplate.update("insert into author (name, active) values ('Reactivation author', false)");

        reactivateAuthor(authorId, null).andExpect(status().isOk());
        reactivateAuthor(authorId, null).andExpect(status().isOk());
    }
}
