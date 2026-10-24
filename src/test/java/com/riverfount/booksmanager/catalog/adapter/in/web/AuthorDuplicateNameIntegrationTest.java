package com.riverfount.booksmanager.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * An author whose name an active author already has (RF02) through the real path: security chain, transaction and
 * PostgreSQL, with the commits. The registration and the rename are refused with the id of the author that exists,
 * unless the homonym is confirmed; an inactive author does not count. The data is committed, so it is removed after
 * each test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class AuthorDuplicateNameIntegrationTest {

    private static final String PREFIX = "Dup author test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    private long existingId;

    @BeforeEach
    void createAnAuthor() {
        cleanUp();
        existingId = jdbcTemplate.queryForObject(
                "insert into author (name) values (?) returning id", Long.class, PREFIX + " Machado");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from author where name like ?", PREFIX + "%");
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(request
                .header("Authorization", "Bearer " + TestTokens.valid(encoder, properties, "OPERATOR"))
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private int authorsNamed(String name) {
        return jdbcTemplate.queryForObject("select count(*) from author where lower(name) = lower(?)",
                Integer.class, name);
    }

    @Test
    void registeringTheNameOfAnActiveAuthorIgnoringCaseShouldBe422WithTheExistingId() throws Exception {
        send(post("/api/v1/authors"), "{\"name\":\"" + PREFIX.toUpperCase() + " MACHADO\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.existingAuthorId").value(existingId))
                .andExpect(jsonPath("$.detail").value(containsString("allowDuplicate")));

        assertThat(authorsNamed(PREFIX + " Machado")).isEqualTo(1);
    }

    @Test
    void aConfirmedHomonymShouldBeRegistered() throws Exception {
        send(post("/api/v1/authors"), "{\"name\":\"" + PREFIX + " Machado\",\"allowDuplicate\":true}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(PREFIX + " Machado"));

        assertThat(authorsNamed(PREFIX + " Machado")).isEqualTo(2);
    }

    @Test
    void anInactiveAuthorWithTheSameNameShouldNotBlock() throws Exception {
        jdbcTemplate.update("update author set active = false where id = ?", existingId);

        send(post("/api/v1/authors"), "{\"name\":\"" + PREFIX + " Machado\"}").andExpect(status().isCreated());
    }

    @Test
    void renamingToTheNameOfAnotherActiveAuthorShouldBe422AndChangeNothing() throws Exception {
        var otherId = jdbcTemplate.queryForObject(
                "insert into author (name) values (?) returning id", Long.class, PREFIX + " Clarice");

        send(patch("/api/v1/authors/" + otherId), "{\"name\":\"" + PREFIX + " machado\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.existingAuthorId").value(existingId));

        assertThat(jdbcTemplate.queryForObject("select name from author where id = ?", String.class, otherId))
                .isEqualTo(PREFIX + " Clarice");
    }

    @Test
    void aConfirmedRenameShouldWorkAndKeepingTheOwnNameIsNotADuplicate() throws Exception {
        var otherId = jdbcTemplate.queryForObject(
                "insert into author (name) values (?) returning id", Long.class, PREFIX + " Clarice");

        send(patch("/api/v1/authors/" + otherId),
                "{\"name\":\"" + PREFIX + " Machado\",\"allowDuplicate\":true}").andExpect(status().isOk());
        send(patch("/api/v1/authors/" + existingId), "{\"name\":\"" + PREFIX + " MACHADO\"}")
                .andExpect(status().isUnprocessableContent());
        send(patch("/api/v1/authors/" + otherId), "{\"name\":\"" + PREFIX + " Machado\"}")
                .andExpect(status().isUnprocessableContent()); // another one (the existing) has it
    }

    @Test
    void anAuthorShouldBeAbleToChangeOnlyTheCaseOfItsOwnName() throws Exception {
        send(patch("/api/v1/authors/" + existingId), "{\"name\":\"" + PREFIX + " MACHADO\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(PREFIX + " MACHADO"));
    }
}
