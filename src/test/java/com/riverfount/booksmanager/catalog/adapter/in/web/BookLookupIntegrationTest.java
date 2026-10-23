package com.riverfount.booksmanager.catalog.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
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
 * The lookup of an ISBN (RF06, RNF19) through the real path: the security chain, the controller, the service and
 * PostgreSQL. The outside lookup is off in the tests, so no network is touched: what is shown here is that an ISBN
 * already in the collection (even an inactive book) is reported, that an invalid one is a 400, and that without
 * the outside source the answer is a plain 200 and not an error. The suggestion itself is covered with the port
 * faked in the service and web tests.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class BookLookupIntegrationTest {

    private static final String ISBN = "9780000000309";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    private long bookId;

    @BeforeEach
    void createABook() {
        cleanUp();
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Book lookup test') returning id", Long.class);
        bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id, active) values (?, 'Registered', ?, false) returning id",
                Long.class, ISBN, categoryId);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from book where isbn = ?", ISBN);
        jdbcTemplate.update("delete from category where name = 'Book lookup test'");
    }

    private ResultActions lookup(String role, String isbn) throws Exception {
        var request = get("/api/v1/books/lookup").param("isbn", isbn);
        if (role != null) {
            request.header("Authorization", "Bearer " + TestTokens.valid(encoder, properties, role));
        }
        return mockMvc.perform(request);
    }

    @Test
    void anIsbnAlreadyInTheCollectionShouldBeReportedEvenForAnInactiveBook() throws Exception {
        lookup("OPERATOR", "978-0-00-000030-9").andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.registeredBookId").value(bookId))
                .andExpect(jsonPath("$.isbn").value(ISBN));
    }

    @Test
    void anIsbnTheCollectionDoesNotHaveShouldGetAPlain200WithoutTheOutsideSource() throws Exception {
        lookup("ADMIN", "9780000000026").andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(containsString("manualmente")));
    }

    @Test
    void anInvalidIsbnShouldBeA400WithAProblemDetail() throws Exception {
        lookup("OPERATOR", "9780000000027").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("dígito verificador")));
        lookup("OPERATOR", "123").andExpect(status().isBadRequest());
    }

    @Test
    void shouldRequireAToken() throws Exception {
        lookup(null, ISBN).andExpect(status().isUnauthorized());
    }
}
