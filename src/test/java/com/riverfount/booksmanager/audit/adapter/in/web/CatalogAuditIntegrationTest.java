package com.riverfount.booksmanager.audit.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.user.application.port.out.PasswordHasher;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
 * Deactivating and reactivating a book, an author, a category and a copy leave an entry in the audit trail (RF27,
 * RN14), through the real path: the security chain, the transactions and PostgreSQL, with the commits. The operator
 * deactivates and the administrator reactivates, and both show up as the author of the entry; what did not change the
 * state, and what was refused, leaves nothing; and the check constraints of the table accept the new actions. The
 * data is committed, so it is removed after each test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class CatalogAuditIntegrationTest {

    private static final String MARKER = "Catalog audit test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    @Autowired
    private PasswordHasher passwordHasher;

    private long adminId;
    private long operatorId;
    private long categoryId;
    private long authorId;
    private long bookId;
    private long copyId;

    @BeforeEach
    void createTheCatalogAndTheUsers() {
        cleanUp();
        adminId = newUser("cataudit-admin", "ADMIN");
        operatorId = newUser("cataudit-operator", "OPERATOR");
        categoryId = jdbcTemplate.queryForObject("insert into category (name) values (?) returning id", Long.class,
                MARKER);
        authorId = jdbcTemplate.queryForObject("insert into author (name) values (?) returning id", Long.class,
                MARKER + " author");
        bookId = jdbcTemplate.queryForObject("""
                insert into book (isbn, title, publisher, publication_year, category_id)
                values ('9780000000378', ?, 'Editora', 1999, ?) returning id
                """, Long.class, MARKER + " book", categoryId);
        jdbcTemplate.update("insert into book_author (book_id, author_id) values (?, ?)", bookId, authorId);
        copyId = jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, 'CAT-AUDIT-1', 'AVAILABLE') returning id",
                Long.class, bookId);
        jdbcTemplate.update("delete from audit_log");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from audit_log");
        jdbcTemplate.update("delete from loan where copy_id in (select id from copy where asset_code like 'CAT-AUDIT-%')");
        jdbcTemplate.update("delete from copy where asset_code like 'CAT-AUDIT-%'");
        jdbcTemplate.update("delete from reader where document like 'CAT-AUDIT-DOC-%'");
        jdbcTemplate.update("delete from book_author where author_id in (select id from author where name like ?)",
                MARKER + "%");
        jdbcTemplate.update("delete from book where isbn = '9780000000378'");
        jdbcTemplate.update("delete from author where name like ?", MARKER + "%");
        jdbcTemplate.update("delete from category where name = ?", MARKER);
        jdbcTemplate.update("delete from app_user where login like 'cataudit-%'");
    }

    private long newUser(String login, String role) {
        return jdbcTemplate.queryForObject("""
                insert into app_user (login, password_hash, role) values (?, ?, ?) returning id
                """, Long.class, login, passwordHasher.hash("Cat4udit#Passw0rd"), role);
    }

    private String token(long userId, String role) {
        return "Bearer " + TestTokens.signed(encoder, properties.issuer(), String.valueOf(userId), role, Instant.now(),
                Instant.now().plusSeconds(900));
    }

    private ResultActions asOperator(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header("Authorization", token(operatorId, "OPERATOR"))
                .header("X-Requested-With", "XMLHttpRequest"));
    }

    private ResultActions asAdmin(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header("Authorization", token(adminId, "ADMIN"))
                .header("X-Requested-With", "XMLHttpRequest"));
    }

    private List<Map<String, Object>> trail() {
        return jdbcTemplate.queryForList("select * from audit_log order by id");
    }

    @Test
    void theOperatorDeactivatingAndTheAdministratorReactivatingABookLeaveTwoEntriesWithTheTitle() throws Exception {
        asOperator(delete("/api/v1/books/" + bookId)).andExpect(status().isNoContent());
        // categories and authors are active, so the book can come back
        asAdmin(post("/api/v1/books/" + bookId + "/reactivate")).andExpect(status().isOk());

        var rows = trail();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("action", "BOOK_DEACTIVATED").containsEntry("target_type", "BOOK")
                .containsEntry("target_id", bookId).containsEntry("summary", MARKER + " book")
                .containsEntry("actor_id", operatorId).containsEntry("actor_login", "cataudit-operator");
        assertThat(rows.get(1)).containsEntry("action", "BOOK_REACTIVATED").containsEntry("actor_id", adminId)
                .containsEntry("actor_login", "cataudit-admin");
    }

    @Test
    void everyResourceLeavesItsDeactivationAndReactivationWithTheRightSummary() throws Exception {
        asOperator(delete("/api/v1/copies/" + copyId)).andExpect(status().isNoContent());
        asOperator(delete("/api/v1/books/" + bookId)).andExpect(status().isNoContent());
        asOperator(delete("/api/v1/authors/" + authorId)).andExpect(status().isNoContent());
        asOperator(delete("/api/v1/categories/" + categoryId)).andExpect(status().isNoContent());

        asAdmin(post("/api/v1/categories/" + categoryId + "/reactivate")).andExpect(status().isOk());
        asAdmin(post("/api/v1/authors/" + authorId + "/reactivate")).andExpect(status().isOk());
        asAdmin(post("/api/v1/books/" + bookId + "/reactivate")).andExpect(status().isOk());
        asAdmin(post("/api/v1/copies/" + copyId + "/reactivate")).andExpect(status().isOk());

        assertThat(trail()).extracting(row -> row.get("action")).containsExactly("COPY_DEACTIVATED",
                "BOOK_DEACTIVATED", "AUTHOR_DEACTIVATED", "CATEGORY_DEACTIVATED", "CATEGORY_REACTIVATED",
                "AUTHOR_REACTIVATED", "BOOK_REACTIVATED", "COPY_REACTIVATED");
        assertThat(trail()).extracting(row -> row.get("summary")).containsExactly("CAT-AUDIT-1", MARKER + " book",
                MARKER + " author", MARKER, MARKER, MARKER + " author", MARKER + " book", "CAT-AUDIT-1");
        assertThat(trail()).extracting(row -> row.get("target_type")).containsExactly("COPY", "BOOK", "AUTHOR",
                "CATEGORY", "CATEGORY", "AUTHOR", "BOOK", "COPY");
    }

    @Test
    void repeatingTheDeactivationOrReactivatingWhatIsActiveLeavesNothingMore() throws Exception {
        asOperator(delete("/api/v1/authors/" + authorId)).andExpect(status().isNoContent());
        asOperator(delete("/api/v1/authors/" + authorId)).andExpect(status().isNoContent());
        asAdmin(post("/api/v1/books/" + bookId + "/reactivate")).andExpect(status().isOk());
        asAdmin(post("/api/v1/categories/" + categoryId + "/reactivate")).andExpect(status().isOk());

        assertThat(trail()).hasSize(1);
        assertThat(trail().get(0)).containsEntry("action", "AUTHOR_DEACTIVATED");
    }

    @Test
    void aRefusedReactivationLeavesNothing() throws Exception {
        asOperator(delete("/api/v1/books/" + bookId)).andExpect(status().isNoContent());
        asOperator(delete("/api/v1/categories/" + categoryId)).andExpect(status().isNoContent());
        var before = trail().size();

        asAdmin(post("/api/v1/books/" + bookId + "/reactivate")).andExpect(status().isUnprocessableContent());

        assertThat(trail()).hasSize(before);
        assertThat(jdbcTemplate.queryForObject("select active from book where id = ?", Boolean.class, bookId))
                .isFalse();
    }

    @Test
    void aDeactivationRefusedBecauseTheCopyIsOnLoanLeavesNothing() throws Exception {
        var readerId = jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', 'CAT-AUDIT-DOC-1', 'r@example.com', date '1990-01-01') returning id
                """, Long.class);
        jdbcTemplate.update("update copy set status = 'BORROWED' where id = ?", copyId);
        jdbcTemplate.update("insert into loan (copy_id, reader_id, pickup_date, due_date) values (?, ?, ?, ?)",
                copyId, readerId, java.time.LocalDate.now().minusDays(1), java.time.LocalDate.now().plusDays(13));

        asOperator(delete("/api/v1/copies/" + copyId)).andExpect(status().isUnprocessableContent());

        assertThat(trail()).isEmpty();
    }

    @Test
    void anUnknownIdLeavesNothing() throws Exception {
        asOperator(delete("/api/v1/books/999999")).andExpect(status().isNotFound());
        asAdmin(post("/api/v1/copies/999999/reactivate")).andExpect(status().isNotFound());

        assertThat(trail()).isEmpty();
    }

    @Test
    void onlyTheAdministratorReadsTheNewEntriesAndTheFiltersAcceptTheNewValues() throws Exception {
        asOperator(delete("/api/v1/books/" + bookId)).andExpect(status().isNoContent());
        asOperator(delete("/api/v1/authors/" + authorId)).andExpect(status().isNoContent());

        asOperator(get("/api/v1/audit-log").param("action", "BOOK_DEACTIVATED")).andExpect(status().isForbidden());
        asAdmin(get("/api/v1/audit-log").param("action", "BOOK_DEACTIVATED").param("targetType", "BOOK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].action").value("BOOK_DEACTIVATED"))
                .andExpect(jsonPath("$.items[0].targetType").value("BOOK"))
                .andExpect(jsonPath("$.items[0].targetId").value(bookId))
                .andExpect(jsonPath("$.items[0].summary").value(MARKER + " book"))
                .andExpect(jsonPath("$.items[0].actorLogin").value("cataudit-operator"));
        asAdmin(get("/api/v1/audit-log").param("targetType", "AUTHOR")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].action").value("AUTHOR_DEACTIVATED"));
    }
}
