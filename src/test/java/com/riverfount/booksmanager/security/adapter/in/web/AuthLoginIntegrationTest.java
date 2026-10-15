package com.riverfount.booksmanager.security.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import com.riverfount.booksmanager.security.domain.TokenHash;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The login (RF08, HU05) from the HTTP request to the database, with the real
 * security chain, services, adapters and PostgreSQL. Each test runs in a
 * transaction that is rolled back.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
// MockMvc prints the requests and responses of a failing test; here that would put logins, passwords and tokens in the
// test output, and into the captured log that the last test checks for secrets.
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class AuthLoginIntegrationTest {

    private static final String PASSWORD = "S3cret-Pass!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    private Long mariaId;

    @BeforeEach
    void setUp() {
        mariaId = insertUser("maria", "OPERATOR", true);
    }

    private Long insertUser(String login, String role, boolean active) {
        return jdbcTemplate.queryForObject(
                "insert into app_user (login, password_hash, role, active) values (?, ?, ?, ?) returning id",
                Long.class, login, new BCryptPasswordEncoder().encode(PASSWORD), role, active);
    }

    private ResultActions login(String login, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"));
    }

    private static String accessTokenIn(String body) {
        return JsonPath.read(body, "$.accessToken");
    }

    private static String refreshTokenIn(MockHttpServletResponse response) {
        var header = response.getHeader("Set-Cookie");
        assertThat(header).startsWith("refresh_token=");
        return header.substring("refresh_token=".length(), header.indexOf(';'));
    }

    private int refreshTokenRows() {
        return jdbcTemplate.queryForObject("select count(*) from refresh_token", Integer.class);
    }

    // success

    @Test
    void shouldLogInAndGiveAnAccessTokenTheSecurityChainAccepts() throws Exception {
        var response = login("maria", PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse();

        var accessToken = accessTokenIn(response.getContentAsString());
        var jwt = jwtDecoder.decode(accessToken);
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(mariaId));
        assertThat(jwt.getClaimAsString("role")).isEqualTo("OPERATOR");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("booksmanager");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        mockMvc.perform(get("/api/v1/books").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void shouldSendTheRefreshTokenOnlyInAnHttpOnlyCookie() throws Exception {
        var response = login("maria", PASSWORD).andExpect(status().isOk()).andReturn().getResponse();

        var cookie = response.getHeader("Set-Cookie");
        assertThat(cookie).contains("HttpOnly").contains("Secure").contains("SameSite=Strict")
                .contains("Path=/api/v1/auth").contains("Max-Age=" + Duration.ofDays(7).toSeconds());
        assertThat(response.getContentAsString()).doesNotContain(refreshTokenIn(response))
                .doesNotContain("refresh");
        assertThat(response.getHeaders("Set-Cookie")).hasSize(1);
        assertThat(response.getCookie("JSESSIONID")).isNull();
    }

    @Test
    void shouldStoreOnlyTheHashOfTheRefreshTokenForTheUserWithItsLifetime() throws Exception {
        var before = Instant.now();
        var response = login("maria", PASSWORD).andExpect(status().isOk()).andReturn().getResponse();
        var raw = refreshTokenIn(response);

        var row = jdbcTemplate.queryForMap("select user_id, token_hash, created_at, expires_at, revoked_at, "
                + "replaced_by from refresh_token");
        assertThat(row.get("user_id")).isEqualTo(mariaId);
        assertThat(row.get("token_hash")).isEqualTo(TokenHash.sha256Hex(raw)).isNotEqualTo(raw);
        assertThat(row.get("revoked_at")).isNull();
        assertThat(row.get("replaced_by")).isNull();
        var createdAt = ((java.sql.Timestamp) row.get("created_at")).toInstant();
        var expiresAt = ((java.sql.Timestamp) row.get("expires_at")).toInstant();
        assertThat(createdAt).isBetween(before.minusSeconds(1), Instant.now().plusSeconds(1));
        assertThat(Duration.between(createdAt, expiresAt)).isEqualTo(Duration.ofDays(7));
        assertThat(jdbcTemplate.queryForObject("select count(*) from refresh_token where token_hash = ?",
                Integer.class, raw)).isZero();
    }

    @Test
    void shouldNotCacheTheResponseThatCarriesTokens() throws Exception {
        var response = login("maria", PASSWORD).andReturn().getResponse();

        assertThat(response.getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    void eachLoginShouldGetItsOwnRefreshToken() throws Exception {
        var first = refreshTokenIn(login("maria", PASSWORD).andReturn().getResponse());
        var second = refreshTokenIn(login("maria", PASSWORD).andReturn().getResponse());

        assertThat(first).isNotEqualTo(second);
        assertThat(refreshTokenRows()).isEqualTo(2);
    }

    @Test
    void theLoginShouldNotBeCaseSensitive() throws Exception {
        login("  MARIA ", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void anAdministratorGetsTheAdminRoleInTheToken() throws Exception {
        var adminId = insertUser("chefe", "ADMIN", true);

        var body = login("chefe", PASSWORD).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();

        var jwt = jwtDecoder.decode(accessTokenIn(body));
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(adminId));
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
    }

    // refused

    @Test
    void wrongPasswordUnknownLoginAndInactiveUserShouldGetExactlyTheSame401() throws Exception {
        insertUser("inativa", "OPERATOR", false);

        var wrongPassword = login("maria", "Wrong-Pass1!").andExpect(status().isUnauthorized()).andReturn()
                .getResponse();
        var unknownLogin = login("nobody", PASSWORD).andExpect(status().isUnauthorized()).andReturn().getResponse();
        var inactive = login("inativa", PASSWORD).andExpect(status().isUnauthorized()).andReturn().getResponse();

        assertThat(wrongPassword.getContentAsString()).isEqualTo(unknownLogin.getContentAsString())
                .isEqualTo(inactive.getContentAsString());
        assertThat(wrongPassword.getContentType()).startsWith("application/problem+json");
        assertThat(wrongPassword.getContentAsString()).contains("Login ou senha inválidos.")
                .doesNotContain("maria").doesNotContain("nobody").doesNotContain("inativa");
    }

    @Test
    void aRefusedLoginShouldSetNoCookieAndStoreNoToken() throws Exception {
        insertUser("inativa", "OPERATOR", false);

        for (var attempt : List.of(login("maria", "Wrong-Pass1!"), login("nobody", PASSWORD),
                login("inativa", PASSWORD))) {
            assertThat(attempt.andReturn().getResponse().getHeaders("Set-Cookie")).isEmpty();
        }

        assertThat(refreshTokenRows()).isZero();
    }

    @Test
    void missingFieldsShouldBeA400WithoutCookieOrToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(refreshTokenRows()).isZero();
    }

    // the Authorization header is ignored on the authentication routes

    @Test
    void anExpiredAccessTokenSentByTheClientShouldNotStopANewLogin() throws Exception {
        var expired = TestTokens.expired(jwtEncoder, jwtProperties, Duration.ofHours(1));

        mockMvc.perform(post("/api/v1/auth/login").header("Authorization", "Bearer " + expired)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"maria\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void garbageInTheAuthorizationHeaderShouldNotStopALoginEither() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").header("Authorization", "Bearer garbage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"maria\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void theOtherRoutesShouldStillRefuseAnInvalidAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/books").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    // lockout after three wrong passwords (RF08)

    private void wrongPasswordTimes(String login, int times) throws Exception {
        for (var i = 0; i < times; i++) {
            login(login, "Wrong-Pass1!").andExpect(status().isUnauthorized());
        }
    }

    @Test
    void threeWrongPasswordsShouldBlockTheUserEvenForTheRightPassword() throws Exception {
        wrongPasswordTimes("maria", 3);

        var blocked = login("maria", PASSWORD).andExpect(status().isUnauthorized()).andReturn().getResponse();

        assertThat(blocked.getHeaders("Set-Cookie")).isEmpty();
        assertThat(refreshTokenRows()).isZero();
        var row = jdbcTemplate.queryForMap(
                "select failed_login_attempts, blocked_until from app_user where login = 'maria'");
        assertThat(row.get("failed_login_attempts")).isEqualTo(3);
        assertThat(((java.sql.Timestamp) row.get("blocked_until")).toInstant())
                .isBetween(Instant.now().plus(Duration.ofMinutes(14)), Instant.now().plus(Duration.ofMinutes(16)));
    }

    @Test
    void twoWrongPasswordsShouldNotBlockAndALoginShouldResetTheCount() throws Exception {
        wrongPasswordTimes("maria", 2);

        login("maria", PASSWORD).andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("select failed_login_attempts from app_user where login = 'maria'",
                Integer.class)).isZero();
        wrongPasswordTimes("maria", 2);
        login("maria", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void theRefusalOfABlockedUserShouldBeIdenticalToThatOfAnUnknownLogin() throws Exception {
        wrongPasswordTimes("maria", 3);

        var blocked = login("maria", PASSWORD).andReturn().getResponse().getContentAsString();
        var unknown = login("nobody", PASSWORD).andReturn().getResponse().getContentAsString();

        assertThat(blocked).isEqualTo(unknown).contains("Login ou senha inválidos.");
    }

    @Test
    void theBlockEndsWhenItsTimeIsUp() throws Exception {
        wrongPasswordTimes("maria", 3);
        entityManager.flush();
        jdbcTemplate.update("update app_user set blocked_until = now() - interval '1 second' where login = 'maria'");
        // the raw SQL bypasses the session, which would still hand back the user it already loaded
        entityManager.clear();

        login("maria", PASSWORD).andExpect(status().isOk());

        var row = jdbcTemplate.queryForMap(
                "select failed_login_attempts, blocked_until from app_user where login = 'maria'");
        assertThat(row.get("failed_login_attempts")).isEqualTo(0);
        assertThat(row.get("blocked_until")).isNull();
    }

    @Test
    void wrongPasswordsOfAnInactiveUserAreNotCounted() throws Exception {
        insertUser("inativa", "OPERATOR", false);

        wrongPasswordTimes("inativa", 4);

        assertThat(jdbcTemplate.queryForObject("select failed_login_attempts from app_user where login = 'inativa'",
                Integer.class)).isZero();
    }

    // no secret in the log

    @Test
    void neitherThePasswordNorTheTokensShouldReachTheLog(CapturedOutput output) throws Exception {
        var ok = login("maria", PASSWORD).andReturn().getResponse();
        login("maria", "Wrong-Pass1!");
        login("nobody", "Another-Pass2!");

        var accessToken = accessTokenIn(ok.getContentAsString());
        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain("Wrong-Pass1!")
                .doesNotContain("Another-Pass2!").doesNotContain(accessToken)
                .doesNotContain(refreshTokenIn(ok));
    }
}
