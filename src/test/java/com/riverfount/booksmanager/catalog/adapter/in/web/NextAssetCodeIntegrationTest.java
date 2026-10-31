package com.riverfount.booksmanager.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
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
 * The suggestion of the next asset code (RN02), through the whole stack: the real security chain, the real PostgreSQL
 * and the library configuration of the {@code loan} module, which gives the prefix to the {@code catalog} through its
 * own port. Each test runs in a transaction that is rolled back.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class NextAssetCodeIntegrationTest {

    private static final String URL = "/api/v1/copies/next-asset-code";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    private long bookId;

    @BeforeEach
    void setUp() {
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name, active) values ('Ficção', true) returning id", Long.class);
        bookId = jdbcTemplate.queryForObject("insert into book (isbn, title, category_id, active) "
                + "values ('9788533302273', 'Dom Casmurro', ?, true) returning id", Long.class, categoryId);
    }

    private ResultActions suggestion(String role) throws Exception {
        return mockMvc.perform(get(URL).header("Authorization", "Bearer " + TestTokens.valid(encoder, properties, role)));
    }

    private void copy(String assetCode, boolean active) {
        jdbcTemplate.update("insert into copy (book_id, asset_code, status, active) values (?, ?, 'AVAILABLE', ?)",
                bookId, assetCode, active);
    }

    @Test
    void shouldSuggestTheFirstCodeWithTheDefaultPrefixWhenThereAreNoCopies() throws Exception {
        suggestion("ADMIN").andExpect(status().isOk()).andExpect(jsonPath("$.assetCode").value("PAT-00001"));
    }

    @Test
    void anOperatorShouldGetTheSuggestionToo() throws Exception {
        suggestion("OPERATOR").andExpect(status().isOk()).andExpect(jsonPath("$.assetCode").value("PAT-00001"));
    }

    @Test
    void shouldSuggestTheHighestNumberOfTheWholeCollectionPlusOne() throws Exception {
        copy("PAT-00001", true);
        copy("PAT-00007", false);
        copy("LIVRO-9", true);

        suggestion("OPERATOR").andExpect(status().isOk()).andExpect(jsonPath("$.assetCode").value("PAT-00008"));
    }

    @Test
    void shouldFollowThePrefixOfTheLibraryConfiguration() throws Exception {
        copy("PAT-00007", true);
        jdbcTemplate.update("update library_configuration set asset_code_prefix = 'BIB'");

        suggestion("ADMIN").andExpect(status().isOk()).andExpect(jsonPath("$.assetCode").value("BIB-00001"));
    }

    @Test
    void aReaderShouldBeRefused() throws Exception {
        suggestion("READER").andExpect(status().isForbidden());
    }

    @Test
    void withoutATokenTheRequestShouldBeRefused() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void theNumberOfQueriesShouldNotGrowWithTheNumberOfCopies() throws Exception {
        copy("PAT-00001", true);
        var few = queriesForASuggestion();

        for (var i = 2; i <= 60; i++) {
            copy("PAT-" + String.format("%05d", i), i % 2 == 0);
        }
        var many = queriesForASuggestion();

        assertThat(many).isEqualTo(few).isLessThanOrEqualTo(2);
    }

    private long queriesForASuggestion() throws Exception {
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        suggestion("OPERATOR").andExpect(status().isOk());

        return statistics.getPrepareStatementCount();
    }
}
