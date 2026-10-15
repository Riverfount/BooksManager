package com.riverfount.booksmanager.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deactivating a system user (RF09, HU28, RN14) with the real chain and
 * PostgreSQL: the record stays, the user can no longer log in or renew, and
 * the refresh tokens are revoked in the same transaction. Each test runs in a
 * transaction that is rolled back. The race between two administrators is in
 * UserDeactivationConcurrencyTest.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class UserDeactivationIntegrationTest {

    private static final String PASSWORD = "S3cret-Pass!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    private Long chefeId;
    private Long mariaId;

    @BeforeEach
    void setUp() {
        chefeId = insertUser("chefe", "ADMIN");
        mariaId = insertUser("maria", "OPERATOR");
    }

    private Long insertUser(String login, String role) {
        return jdbcTemplate.queryForObject(
                "insert into app_user (login, password_hash, role) values (?, ?, ?) returning id",
                Long.class, login, new BCryptPasswordEncoder().encode(PASSWORD), role);
    }

    /** A token as the login would issue it: the subject is the id of the user. */
    private String token(Long userId, String role) {
        return "Bearer " + TestTokens.signed(encoder, properties.issuer(), String.valueOf(userId), role,
                Instant.now(), Instant.now().plusSeconds(900));
    }

    private ResultActions deactivate(Long actingUserId, String role, Long id) throws Exception {
        return mockMvc.perform(delete("/api/v1/users/" + id).header("Authorization", token(actingUserId, role)));
    }

    private ResultActions login(String login) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"password\":\"" + PASSWORD + "\"}"));
    }

    private boolean isActive(Long id) {
        return jdbcTemplate.queryForObject("select active from app_user where id = ?", Boolean.class, id);
    }

    private static String refreshTokenIn(String setCookie) {
        return setCookie.substring("refresh_token=".length(), setCookie.indexOf(';'));
    }

    // the effect

    @Test
    void anAdministratorShouldDeactivateAUserKeepingTheRecord() throws Exception {
        var before = jdbcTemplate.queryForMap("select login, role, password_hash from app_user where id = ?", mariaId);

        deactivate(chefeId, "ADMIN", mariaId).andExpect(status().isNoContent());

        assertThat(isActive(mariaId)).isFalse();
        assertThat(jdbcTemplate.queryForMap("select login, role, password_hash from app_user where id = ?", mariaId))
                .isEqualTo(before);
        assertThat(jdbcTemplate.queryForObject("select count(*) from app_user where id = ?", Integer.class, mariaId))
                .isEqualTo(1);
    }

    @Test
    void theInactiveUserCanNoLongerLogInAndThePasswordIsNotWhatStopsIt() throws Exception {
        login("maria").andExpect(status().isOk());

        deactivate(chefeId, "ADMIN", mariaId).andExpect(status().isNoContent());

        login("maria").andExpect(status().isUnauthorized());
    }

    @Test
    void theInactiveUserCanNoLongerRenewAndEveryRefreshTokenIsRevoked() throws Exception {
        var first = refreshTokenIn(login("maria").andExpect(status().isOk()).andReturn().getResponse()
                .getHeader("Set-Cookie"));
        var second = refreshTokenIn(login("maria").andReturn().getResponse().getHeader("Set-Cookie"));

        deactivate(chefeId, "ADMIN", mariaId).andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from refresh_token where user_id = ? and revoked_at is null", Integer.class, mariaId))
                .isZero();
        for (var token : new String[] {first, second}) {
            mockMvc.perform(post("/api/v1/auth/refresh").header("X-Requested-With", "XMLHttpRequest")
                            .cookie(new Cookie("refresh_token", token)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void theAccessTokenAlreadyIssuedKeepsWorkingUntilItExpiresTheKnownLimitOfAStatelessToken() throws Exception {
        var accessToken = JsonPath.<String>read(login("maria").andReturn().getResponse().getContentAsString(),
                "$.accessToken");

        deactivate(chefeId, "ADMIN", mariaId).andExpect(status().isNoContent());

        // no lookup per request: the token is trusted until it expires (at most 15 minutes);
        // what the deactivation stops is a new login and a renewal
        mockMvc.perform(get("/api/v1/books").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void deactivatingAnInactiveUserAgainIs204() throws Exception {
        deactivate(chefeId, "ADMIN", mariaId).andExpect(status().isNoContent());
        deactivate(chefeId, "ADMIN", mariaId).andExpect(status().isNoContent());

        assertThat(isActive(mariaId)).isFalse();
    }

    @Test
    void shouldDeactivateAUserBlockedByTheLoginLockoutToo() throws Exception {
        for (var i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"login\":\"maria\",\"password\":\"Wrong-Pass1!\"}")).andExpect(status().isUnauthorized());
        }

        deactivate(chefeId, "ADMIN", mariaId).andExpect(status().isNoContent());

        assertThat(isActive(mariaId)).isFalse();
    }

    @Test
    void anInactiveUserNoLongerAppearsInTheDefaultListButIsStillFoundById() throws Exception {
        deactivate(chefeId, "ADMIN", mariaId).andExpect(status().isNoContent());

        var list = mockMvc.perform(get("/api/v1/users").header("Authorization", token(chefeId, "ADMIN")))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<java.util.List<String>>read(list, "$.items[*].login")).containsExactly("chefe");
        mockMvc.perform(get("/api/v1/users/" + mariaId).header("Authorization", token(chefeId, "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
    }

    // the rules

    @Test
    void shouldRefuseToDeactivateYourselfWith422() throws Exception {
        insertUser("vice", "ADMIN");

        var body = deactivate(chefeId, "ADMIN", chefeId).andExpect(status().isUnprocessableContent())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("próprio usuário");
        assertThat(isActive(chefeId)).isTrue();
    }

    @Test
    void shouldRefuseToDeactivateTheLastActiveAdministratorWith422() throws Exception {
        // the caller is an administrator who is no longer in the database: a token issued before the
        // deactivation still works for a few minutes, and it must not be able to remove the last one
        var body = deactivate(999_999L, "ADMIN", chefeId).andExpect(status().isUnprocessableContent())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("último administrador");
        assertThat(isActive(chefeId)).isTrue();
    }

    @Test
    void shouldDeactivateAnAdministratorWhenAnotherActiveOneRemains() throws Exception {
        var viceId = insertUser("vice", "ADMIN");

        deactivate(viceId, "ADMIN", chefeId).andExpect(status().isNoContent());

        assertThat(isActive(chefeId)).isFalse();
    }

    @Test
    void shouldReturn404ForAnUnknownUserAnd400ForANonNumericId() throws Exception {
        deactivate(chefeId, "ADMIN", 999_999L).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/users/abc").header("Authorization", token(chefeId, "ADMIN")))
                .andExpect(status().isBadRequest());
    }

    // only an administrator (RNF08)

    @Test
    void anOperatorAndAnUnknownProfileGet403AndNoTokenGets401AndNothingChanges() throws Exception {
        deactivate(mariaId, "OPERATOR", chefeId).andExpect(status().isForbidden());
        deactivate(mariaId, "SUPERUSER", chefeId).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/users/" + chefeId)).andExpect(status().isUnauthorized());

        assertThat(isActive(chefeId)).isTrue();
    }
}
