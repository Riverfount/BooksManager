package com.riverfount.booksmanager.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Editing the profile of a system user and unblocking one (RF08, RF09, HU28)
 * with the real chain and PostgreSQL. Each test runs in a transaction that is
 * rolled back. The race between two administrators is in UserRoleConcurrencyTest.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class UserEditIntegrationTest {

    private static final String PASSWORD = "S3cret-Pass!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtDecoder decoder;

    @Autowired
    private JwtProperties properties;

    private Long chefeId;
    private Long mariaId;

    @BeforeEach
    void setUp() {
        chefeId = insertUser("chefe", "ADMIN", true);
        mariaId = insertUser("maria", "OPERATOR", true);
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

    private ResultActions edit(String role, Long id, String json) throws Exception {
        return mockMvc.perform(patch("/api/v1/users/" + id).header("Authorization", token(role))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions unblock(String role, Long id) throws Exception {
        return mockMvc.perform(post("/api/v1/users/" + id + "/unblock").header("Authorization", token(role)));
    }

    private ResultActions login(String login, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"));
    }

    private String roleOf(String login) {
        return jdbcTemplate.queryForObject("select role from app_user where login = ?", String.class, login);
    }

    // editing the profile

    @Test
    void anAdministratorShouldPromoteAnOperator() throws Exception {
        var body = edit("ADMIN", mariaId, "{\"role\":\"ADMIN\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("maria")).andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();

        assertThat(roleOf("maria")).isEqualTo("ADMIN");
        assertThat(body).doesNotContain("assword").doesNotContain("hash");
    }

    @Test
    void theNewProfileReachesTheNextLoginAndTheNextRenewalOfTheSession() throws Exception {
        var loginResponse = login("maria", PASSWORD).andExpect(status().isOk()).andReturn().getResponse();
        var cookie = loginResponse.getHeader("Set-Cookie");
        var refreshToken = cookie.substring("refresh_token=".length(), cookie.indexOf(';'));
        var tokenBefore = JsonPath.<String>read(loginResponse.getContentAsString(), "$.accessToken");
        assertThat(decoder.decode(tokenBefore).getClaimAsString("role")).isEqualTo("OPERATOR");

        edit("ADMIN", mariaId, "{\"role\":\"ADMIN\"}").andExpect(status().isOk());

        // the access token already issued keeps its profile until it expires; a new login and a renewal get the new one
        assertThat(decoder.decode(tokenBefore).getClaimAsString("role")).isEqualTo("OPERATOR");
        var again = login("maria", PASSWORD).andReturn().getResponse().getContentAsString();
        assertThat(decoder.decode(JsonPath.<String>read(again, "$.accessToken")).getClaimAsString("role"))
                .isEqualTo("ADMIN");
        var renewed = mockMvc.perform(post("/api/v1/auth/refresh").header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(decoder.decode(JsonPath.<String>read(renewed, "$.accessToken")).getClaimAsString("role"))
                .isEqualTo("ADMIN");
    }

    @Test
    void shouldDemoteAnAdministratorWhenAnotherActiveOneRemains() throws Exception {
        insertUser("vice", "ADMIN", true);

        edit("ADMIN", chefeId, "{\"role\":\"OPERATOR\"}").andExpect(status().isOk());

        assertThat(roleOf("chefe")).isEqualTo("OPERATOR");
    }

    @Test
    void shouldRefuseToDemoteTheLastActiveAdministratorWith422() throws Exception {
        var body = edit("ADMIN", chefeId, "{\"role\":\"OPERATOR\"}").andExpect(status().isUnprocessableContent())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("último administrador");
        assertThat(roleOf("chefe")).isEqualTo("ADMIN");
    }

    @Test
    void anInactiveAdministratorDoesNotCountAsTheOneWhoRemains() throws Exception {
        insertUser("antigo", "ADMIN", false);

        edit("ADMIN", chefeId, "{\"role\":\"OPERATOR\"}").andExpect(status().isUnprocessableContent());

        assertThat(roleOf("chefe")).isEqualTo("ADMIN");
    }

    @Test
    void aBlockedAdministratorStillCountsAsOne() throws Exception {
        var viceId = insertUser("vice", "ADMIN", true);
        for (var i = 0; i < 3; i++) {
            login("vice", "Wrong-Pass1!").andExpect(status().isUnauthorized());
        }

        edit("ADMIN", chefeId, "{\"role\":\"OPERATOR\"}").andExpect(status().isOk());

        assertThat(roleOf("chefe")).isEqualTo("OPERATOR");
        assertThat(jdbcTemplate.queryForObject("select blocked_until is not null from app_user where id = ?",
                Boolean.class, viceId)).isTrue();
    }

    @Test
    void shouldIgnoreWhatThisRouteDoesNotChange() throws Exception {
        var hashBefore = jdbcTemplate.queryForObject("select password_hash from app_user where id = ?", String.class,
                mariaId);

        edit("ADMIN", mariaId, "{\"role\":\"OPERATOR\",\"login\":\"intruso\",\"password\":\"N3w-Pass#word\","
                + "\"active\":false}").andExpect(status().isOk());

        var row = jdbcTemplate.queryForMap("select login, password_hash, active from app_user where id = ?", mariaId);
        assertThat(row.get("login")).isEqualTo("maria");
        assertThat(row.get("password_hash")).isEqualTo(hashBefore);
        assertThat(row.get("active")).isEqualTo(true);
    }

    @Test
    void shouldReturn404ForAnUnknownUserAnd400ForAnUnknownProfile() throws Exception {
        edit("ADMIN", 999_999L, "{\"role\":\"ADMIN\"}").andExpect(status().isNotFound());
        edit("ADMIN", mariaId, "{\"role\":\"SUPERUSER\"}").andExpect(status().isBadRequest());
    }

    // unblocking

    @Test
    void unblockingShouldLetTheRightPasswordInAtOnce() throws Exception {
        for (var i = 0; i < 3; i++) {
            login("maria", "Wrong-Pass1!").andExpect(status().isUnauthorized());
        }
        login("maria", PASSWORD).andExpect(status().isUnauthorized());

        unblock("ADMIN", mariaId).andExpect(status().isNoContent());

        login("maria", PASSWORD).andExpect(status().isOk());
        var row = jdbcTemplate.queryForMap(
                "select failed_login_attempts, blocked_until from app_user where id = ?", mariaId);
        assertThat(row.get("failed_login_attempts")).isEqualTo(0);
        assertThat(row.get("blocked_until")).isNull();
    }

    @Test
    void unblockingIsIdempotentAndKeepsTheOtherDataOfTheUser() throws Exception {
        var before = jdbcTemplate.queryForMap("select login, role, active, password_hash from app_user where id = ?",
                mariaId);

        unblock("ADMIN", mariaId).andExpect(status().isNoContent());
        unblock("ADMIN", mariaId).andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForMap("select login, role, active, password_hash from app_user where id = ?",
                mariaId)).isEqualTo(before);
    }

    @Test
    void aBlockedAdministratorCanBeUnblockedByAnotherAdministrator() throws Exception {
        for (var i = 0; i < 3; i++) {
            login("chefe", "Wrong-Pass1!").andExpect(status().isUnauthorized());
        }

        unblock("ADMIN", chefeId).andExpect(status().isNoContent());

        login("chefe", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void shouldReturn404ForAnUnknownUserOnTheUnblock() throws Exception {
        unblock("ADMIN", 999_999L).andExpect(status().isNotFound());
    }

    // only an administrator (RNF08)

    @Test
    void anOperatorAndAnUnknownProfileGet403AndNothingChanges() throws Exception {
        for (var role : new String[] {"OPERATOR", "SUPERUSER"}) {
            edit(role, mariaId, "{\"role\":\"ADMIN\"}").andExpect(status().isForbidden());
            unblock(role, mariaId).andExpect(status().isForbidden());
        }

        assertThat(roleOf("maria")).isEqualTo("OPERATOR");
        mockMvc.perform(patch("/api/v1/users/" + mariaId).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
