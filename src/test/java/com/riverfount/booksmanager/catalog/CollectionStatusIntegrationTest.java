package com.riverfount.booksmanager.catalog;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import java.time.Instant;
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
 * The collection status report (RF25, RN14) with the real security chain and PostgreSQL. The database is shared
 * with other tests, so each case reads the report before adding its copies and checks the difference. Reads only,
 * so each test runs in a transaction that is rolled back.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class CollectionStatusIntegrationTest {

    private static final String URL = "/api/v1/reports/collection-status";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    private long bookId;
    private int sequence;

    @BeforeEach
    void createABook() {
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Collection status test') returning id", Long.class);
        bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000026', 'T', ?) returning id",
                Long.class, categoryId);
    }

    private void copy(String status, boolean active) {
        jdbcTemplate.update("insert into copy (book_id, asset_code, status, active) values (?, ?, ?, ?)", bookId,
                "CST-" + ++sequence, status, active);
    }

    private ResultActions report(String role) throws Exception {
        var token = "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", role, Instant.now(),
                Instant.now().plusSeconds(900));
        return mockMvc.perform(get(URL).header("Authorization", token));
    }

    private long number(ResultActions result, String path) throws Exception {
        return ((Number) com.jayway.jsonpath.JsonPath.read(
                result.andReturn().getResponse().getContentAsString(), path)).longValue();
    }

    @Test
    void shouldCountTheActiveCopiesByStatusAndTheInactiveApart() throws Exception {
        var before = report("OPERATOR").andExpect(status().isOk());
        var total = number(before, "$.totalCopies");
        var available = number(before, "$.activeByStatus.AVAILABLE");
        var borrowed = number(before, "$.activeByStatus.BORROWED");
        var lost = number(before, "$.activeByStatus.LOST");
        var inactive = number(before, "$.inactiveCopies");

        copy("AVAILABLE", true);
        copy("AVAILABLE", true);
        copy("BORROWED", true);
        copy("LOST", true);
        copy("AVAILABLE", false);
        copy("LOST", false);

        report("OPERATOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCopies").value(total + 4))
                .andExpect(jsonPath("$.activeByStatus.AVAILABLE").value(available + 2))
                .andExpect(jsonPath("$.activeByStatus.BORROWED").value(borrowed + 1))
                .andExpect(jsonPath("$.activeByStatus.LOST").value(lost + 1))
                .andExpect(jsonPath("$.inactiveCopies").value(inactive + 2));
    }

    @Test
    void everyStatusShouldBePresentAndTheCountsShouldAddUpToTheTotal() throws Exception {
        copy("DAMAGED", true);

        var result = report("ADMIN").andExpect(status().isOk())
                .andExpect(jsonPath("$.activeByStatus.AVAILABLE").exists())
                .andExpect(jsonPath("$.activeByStatus.BORROWED").exists())
                .andExpect(jsonPath("$.activeByStatus.RESERVED").exists())
                .andExpect(jsonPath("$.activeByStatus.DAMAGED").exists())
                .andExpect(jsonPath("$.activeByStatus.LOST").exists())
                .andExpect(jsonPath("$.activeByStatus.*", hasSize(5)));

        var sum = number(result, "$.activeByStatus.AVAILABLE") + number(result, "$.activeByStatus.BORROWED")
                + number(result, "$.activeByStatus.RESERVED") + number(result, "$.activeByStatus.DAMAGED")
                + number(result, "$.activeByStatus.LOST");
        org.assertj.core.api.Assertions.assertThat(sum).isEqualTo(number(result, "$.totalCopies"));
    }

    @Test
    void withoutATokenTheAnswerShouldBe401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
