package com.riverfount.booksmanager.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Getting and listing system users (RF09, HU28, RNF14) with the real chain
 * and PostgreSQL. Each test runs in a transaction that is rolled back.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class UserQueryIntegrationTest {

    private static final String PASSWORD = "S3cret-Pass!";

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

    private Long mariaId;

    @BeforeEach
    void setUp() {
        mariaId = insertUser("maria", "OPERATOR", true);
        insertUser("pedro", "ADMIN", true);
        insertUser("antiga", "OPERATOR", false);
    }

    private Long insertUser(String login, String role, boolean active) {
        return jdbcTemplate.queryForObject(
                "insert into app_user (login, password_hash, role, active) values (?, ?, ?, ?) returning id",
                Long.class, login, new BCryptPasswordEncoder().encode(PASSWORD), role, active);
    }

    private String token(String role) {
        return "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", role, Instant.now(),
                Instant.now().plusSeconds(900));
    }

    private ResultActions as(String role, String route) throws Exception {
        return mockMvc.perform(get(route).header("Authorization", token(role)));
    }

    private void wrongPasswordTimes(String login, int times) throws Exception {
        for (var i = 0; i < times; i++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"login\":\"" + login + "\",\"password\":\"Wrong-Pass1!\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // getting a user

    @Test
    void anAdministratorShouldGetAUserWithoutAnySecret() throws Exception {
        var body = as("ADMIN", "/api/v1/users/" + mariaId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mariaId))
                .andExpect(jsonPath("$.login").value("maria"))
                .andExpect(jsonPath("$.role").value("OPERATOR"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.blocked").value(false))
                .andReturn().getResponse().getContentAsString();
        var hash = jdbcTemplate.queryForObject("select password_hash from app_user where id = ?", String.class,
                mariaId);

        assertThat(body).doesNotContain(hash).doesNotContain("assword").doesNotContain("hash");
    }

    @Test
    void shouldGetAnInactiveUserAndAnUnknownIdIs404() throws Exception {
        var inactive = jdbcTemplate.queryForObject("select id from app_user where login = 'antiga'", Long.class);

        as("ADMIN", "/api/v1/users/" + inactive).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        as("ADMIN", "/api/v1/users/999999").andExpect(status().isNotFound());
    }

    @Test
    void theBlockByWrongPasswordsShouldShowUpAndEndWhenItsTimeIsUp() throws Exception {
        wrongPasswordTimes("maria", 3);

        var blocked = as("ADMIN", "/api/v1/users/" + mariaId).andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(true)).andReturn().getResponse().getContentAsString();
        var until = Instant.parse(JsonPath.<String>read(blocked, "$.blockedUntil"));
        assertThat(until).isBetween(Instant.now().plusSeconds(14 * 60), Instant.now().plusSeconds(16 * 60));

        entityManager.flush();
        jdbcTemplate.update("update app_user set blocked_until = now() - interval '1 second' where id = ?", mariaId);
        entityManager.clear();
        as("ADMIN", "/api/v1/users/" + mariaId).andExpect(jsonPath("$.blocked").value(false))
                .andExpect(jsonPath("$.blockedUntil").doesNotExist());
    }

    // listing

    @Test
    void shouldListTheActiveUsersByLoginByDefault() throws Exception {
        var logins = JsonPath.<List<String>>read(
                as("ADMIN", "/api/v1/users").andExpect(status().isOk()).andReturn().getResponse()
                        .getContentAsString(), "$.items[*].login");

        assertThat(logins).containsExactly("maria", "pedro");
    }

    @Test
    void shouldFilterByPartOfTheLoginAndByInactive() throws Exception {
        var found = as("ADMIN", "/api/v1/users?login=MAR").andReturn().getResponse().getContentAsString();
        var inactive = as("ADMIN", "/api/v1/users?active=false").andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(found, "$.items[*].login")).containsExactly("maria");
        assertThat(JsonPath.<List<String>>read(inactive, "$.items[*].login")).containsExactly("antiga");
    }

    @Test
    void shouldPaginateAndSortAndRefuseWhatIsOutOfBounds() throws Exception {
        var page = as("ADMIN", "/api/v1/users?size=1&page=1&sort=login,desc").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2)).andExpect(jsonPath("$.totalPages").value(2))
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(page, "$.items[*].login")).containsExactly("maria");
        as("ADMIN", "/api/v1/users?size=101").andExpect(status().isBadRequest());
        as("ADMIN", "/api/v1/users?sort=password_hash").andExpect(status().isBadRequest());
    }

    @Test
    void theListShouldNeverCarryAHashOrAPassword() throws Exception {
        var body = as("ADMIN", "/api/v1/users?size=100").andReturn().getResponse().getContentAsString();
        var hashes = jdbcTemplate.queryForList("select password_hash from app_user", String.class);

        assertThat(hashes).isNotEmpty().allSatisfy(hash -> assertThat(body).doesNotContain(hash));
        assertThat(body).doesNotContain("$2").doesNotContain("assword");
    }

    // only an administrator (RNF08)

    @Test
    void anOperatorAndAnUnknownProfileGet403AndNoTokenGets401() throws Exception {
        for (var route : new String[] {"/api/v1/users", "/api/v1/users/" + mariaId}) {
            as("OPERATOR", route).andExpect(status().isForbidden());
            as("SUPERUSER", route).andExpect(status().isForbidden());
            mockMvc.perform(get(route)).andExpect(status().isUnauthorized());
        }
    }
}
