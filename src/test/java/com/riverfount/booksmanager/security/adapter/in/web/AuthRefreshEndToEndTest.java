package com.riverfount.booksmanager.security.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import com.riverfount.booksmanager.security.domain.TokenHash;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Renewing, rotating and logging out (RF08) through the HTTP routes, with the
 * real chain, services and PostgreSQL, and no test transaction: the revocation
 * that follows a refused token has to be committed even though the request
 * ends in an error, and the parallel requests need real, separate
 * transactions. The data is cleaned after each test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ExtendWith(OutputCaptureExtension.class)
class AuthRefreshEndToEndTest {

    private static final String LOGIN = "refresh-e2e";
    private static final String PASSWORD = "S3cret-Pass!";
    private static final String CSRF_HEADER = "X-Requested-With";
    private static final String CSRF_VALUE = "XMLHttpRequest";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    private Long userId;

    @BeforeEach
    void createTheUser() {
        cleanUp();
        userId = jdbcTemplate.queryForObject(
                "insert into app_user (login, password_hash, role) values (?, ?, 'OPERATOR') returning id",
                Long.class, LOGIN, new BCryptPasswordEncoder().encode(PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from refresh_token where user_id in (select id from app_user where login = ?)",
                LOGIN);
        jdbcTemplate.update("delete from app_user where login = ?", LOGIN);
    }

    // helpers

    private static String refreshTokenIn(MockHttpServletResponse response) {
        var header = response.getHeader("Set-Cookie");
        assertThat(header).startsWith("refresh_token=");
        return header.substring("refresh_token=".length(), header.indexOf(';'));
    }

    private String loginAndGetTheRefreshToken() throws Exception {
        var response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + LOGIN + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        return refreshTokenIn(response);
    }

    private MockHttpServletRequestBuilder withCsrfHeader(MockHttpServletRequestBuilder request, String token) {
        request.header(CSRF_HEADER, CSRF_VALUE);
        return token == null ? request : request.cookie(new Cookie("refresh_token", token));
    }

    private ResultActions refresh(String token) throws Exception {
        return mockMvc.perform(withCsrfHeader(post("/api/v1/auth/refresh"), token));
    }

    private ResultActions logout(String token) throws Exception {
        return mockMvc.perform(withCsrfHeader(post("/api/v1/auth/logout"), token));
    }

    private Boolean isRevoked(String rawToken) {
        return jdbcTemplate.queryForObject("select revoked_at is not null from refresh_token where token_hash = ?",
                Boolean.class, TokenHash.sha256Hex(rawToken));
    }

    private int activeTokens() {
        return jdbcTemplate.queryForObject(
                "select count(*) from refresh_token where user_id = ? and revoked_at is null", Integer.class, userId);
    }

    private static String assertClearsTheCookie(MockHttpServletResponse response) {
        var cookie = response.getHeader("Set-Cookie");
        assertThat(cookie).startsWith("refresh_token=;").contains("Max-Age=0").contains("HttpOnly")
                .contains("Secure").contains("SameSite=Strict").contains("Path=/api/v1/auth");
        return cookie;
    }

    // rotation

    @Test
    void shouldRenewAndRotateTheRefreshTokenAndGiveAnAccessTokenTheChainAccepts() throws Exception {
        var first = loginAndGetTheRefreshToken();

        var response = refresh(first).andExpect(status().isOk()).andReturn().getResponse();

        var second = refreshTokenIn(response);
        assertThat(second).isNotEqualTo(first);
        var accessToken = JsonPath.<String>read(response.getContentAsString(), "$.accessToken");
        assertThat(jwtDecoder.decode(accessToken).getSubject()).isEqualTo(String.valueOf(userId));
        mockMvc.perform(get("/api/v1/books").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        assertThat(response.getHeader("Set-Cookie")).contains("HttpOnly").contains("Secure")
                .contains("SameSite=Strict").contains("Path=/api/v1/auth");
        assertThat(response.getContentAsString()).doesNotContain(second);
    }

    @Test
    void shouldRevokeTheUsedTokenPointingToTheNewOneAndKeepOnlyOneActive() throws Exception {
        var first = loginAndGetTheRefreshToken();

        var second = refreshTokenIn(refresh(first).andReturn().getResponse());

        var replacedBy = jdbcTemplate.queryForObject(
                "select replaced_by from refresh_token where token_hash = ?", Long.class, TokenHash.sha256Hex(first));
        var newId = jdbcTemplate.queryForObject("select id from refresh_token where token_hash = ?", Long.class,
                TokenHash.sha256Hex(second));
        assertThat(replacedBy).isEqualTo(newId);
        assertThat(isRevoked(first)).isTrue();
        assertThat(isRevoked(second)).isFalse();
        assertThat(activeTokens()).isEqualTo(1);
    }

    @Test
    void theNewTokenShouldRenewAgain() throws Exception {
        var first = loginAndGetTheRefreshToken();
        var second = refreshTokenIn(refresh(first).andReturn().getResponse());

        refresh(second).andExpect(status().isOk());
    }

    @Test
    void theAccessTokenShouldCarryTheCurrentRoleOfTheUser() throws Exception {
        var first = loginAndGetTheRefreshToken();
        jdbcTemplate.update("update app_user set role = 'ADMIN' where id = ?", userId);

        var body = refresh(first).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(jwtDecoder.decode(JsonPath.<String>read(body, "$.accessToken")).getClaimAsString("role"))
                .isEqualTo("ADMIN");
    }

    // reuse

    @Test
    void reusingARotatedTokenShouldRefuseAndRevokeEveryTokenOfTheUser() throws Exception {
        var first = loginAndGetTheRefreshToken();
        var other = loginAndGetTheRefreshToken();
        var second = refreshTokenIn(refresh(first).andReturn().getResponse());

        var response = refresh(first).andExpect(status().isUnauthorized()).andReturn().getResponse();

        assertClearsTheCookie(response);
        assertThat(isRevoked(second)).as("the token the legitimate holder got").isTrue();
        assertThat(isRevoked(other)).as("a session on another device").isTrue();
        assertThat(activeTokens()).isZero();
        refresh(second).andExpect(status().isUnauthorized());
    }

    // refusals

    @Test
    void shouldRefuseUnknownMissingAndExpiredTokensAndClearTheCookie() throws Exception {
        var valid = loginAndGetTheRefreshToken();
        var expiredOne = loginAndGetTheRefreshToken();
        jdbcTemplate.update("update refresh_token set created_at = now() - interval '8 days', "
                + "expires_at = now() - interval '1 day' where token_hash = ?", TokenHash.sha256Hex(expiredOne));

        assertClearsTheCookie(refresh("never-issued").andExpect(status().isUnauthorized()).andReturn().getResponse());
        assertClearsTheCookie(refresh(null).andExpect(status().isUnauthorized()).andReturn().getResponse());
        assertClearsTheCookie(refresh(expiredOne).andExpect(status().isUnauthorized()).andReturn().getResponse());

        // an expired token is not a reuse: it does not take the user's other sessions down
        assertThat(isRevoked(valid)).isFalse();
        assertThat(isRevoked(expiredOne)).isFalse();
    }

    @Test
    void everyRefusalShouldHaveTheSameBody() throws Exception {
        var expiredOne = loginAndGetTheRefreshToken();
        jdbcTemplate.update("update refresh_token set created_at = now() - interval '8 days', "
                + "expires_at = now() - interval '1 day' where token_hash = ?", TokenHash.sha256Hex(expiredOne));

        var unknown = refresh("never-issued").andReturn().getResponse().getContentAsString();
        var expired = refresh(expiredOne).andReturn().getResponse().getContentAsString();
        var missing = refresh(null).andReturn().getResponse().getContentAsString();

        assertThat(unknown).isEqualTo(expired).isEqualTo(missing).contains("Refresh token inválido");
    }

    @Test
    void anInactiveUserShouldNotRenewAndLosesAllTheTokens() throws Exception {
        var first = loginAndGetTheRefreshToken();
        var other = loginAndGetTheRefreshToken();
        jdbcTemplate.update("update app_user set active = false where id = ?", userId);

        refresh(first).andExpect(status().isUnauthorized());

        assertThat(isRevoked(first)).isTrue();
        assertThat(isRevoked(other)).isTrue();
        assertThat(activeTokens()).isZero();
    }

    @Test
    void aUserBlockedByTheLoginLockoutCanStillRenewASessionThatWasAlreadyOpen() throws Exception {
        var first = loginAndGetTheRefreshToken();
        jdbcTemplate.update("update app_user set failed_login_attempts = 3, "
                + "blocked_until = now() + interval '15 minutes' where id = ?", userId);

        refresh(first).andExpect(status().isOk());
    }

    // the CSRF header

    @Test
    void refreshAndLogoutWithoutTheHeaderShouldBe403AndLeaveTheTokenUntouched() throws Exception {
        var token = loginAndGetTheRefreshToken();

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("refresh_token", token)))
                .andExpect(status().isForbidden());

        assertThat(isRevoked(token)).isFalse();
        refresh(token).andExpect(status().isOk());
    }

    // logout

    @Test
    void logoutShouldRevokeTheTokenAndStopItFromRenewing() throws Exception {
        var token = loginAndGetTheRefreshToken();

        var response = logout(token).andExpect(status().isNoContent()).andReturn().getResponse();

        assertClearsTheCookie(response);
        assertThat(isRevoked(token)).isTrue();
        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutShouldEndOnlyThisSession() throws Exception {
        var thisDevice = loginAndGetTheRefreshToken();
        var otherDevice = loginAndGetTheRefreshToken();

        logout(thisDevice).andExpect(status().isNoContent());

        assertThat(isRevoked(otherDevice)).isFalse();
        refresh(otherDevice).andExpect(status().isOk());
    }

    @Test
    void logoutShouldAlwaysBe204EvenWithoutAnyValidTokenAndSayNothingAboutIt() throws Exception {
        var token = loginAndGetTheRefreshToken();
        logout(token).andExpect(status().isNoContent());

        var again = logout(token).andExpect(status().isNoContent()).andReturn().getResponse();
        var unknown = logout("never-issued").andExpect(status().isNoContent()).andReturn().getResponse();
        var missing = logout(null).andExpect(status().isNoContent()).andReturn().getResponse();

        assertThat(again.getContentAsString()).isEqualTo(unknown.getContentAsString())
                .isEqualTo(missing.getContentAsString());
        assertClearsTheCookie(unknown);
        assertClearsTheCookie(missing);
    }

    // the Authorization header is ignored on these routes

    @Test
    void anExpiredOrBrokenAccessTokenShouldNotStopARefreshOrALogout() throws Exception {
        var token = loginAndGetTheRefreshToken();
        var expired = TestTokens.expired(jwtEncoder, jwtProperties, Duration.ofHours(1));

        mockMvc.perform(withCsrfHeader(post("/api/v1/auth/refresh").header("Authorization", "Bearer " + expired),
                token)).andExpect(status().isOk());
        mockMvc.perform(withCsrfHeader(post("/api/v1/auth/logout").header("Authorization", "Bearer garbage"),
                null)).andExpect(status().isNoContent());
    }

    // concurrency

    @Test
    void parallelRefreshesWithTheSameTokenShouldLetOnlyOneThrough() throws Exception {
        var token = loginAndGetTheRefreshToken();
        var attempts = 8;
        var pool = Executors.newFixedThreadPool(attempts);
        var ready = new CountDownLatch(attempts);
        var go = new CountDownLatch(1);
        var results = new ArrayList<Future<Integer>>();
        try {
            for (var i = 0; i < attempts; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return refresh(token).andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            var succeeded = 0;
            for (var result : results) {
                var status = result.get(60, TimeUnit.SECONDS);
                assertThat(status).isIn(200, 401);
                if (status == 200) {
                    succeeded++;
                }
            }
            // the chain does not fork: exactly one request rotated the token
            assertThat(succeeded).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        // the others used a token that was already revoked, which takes every session of the user down
        assertThat(activeTokens()).isZero();
    }

    // no secret in the log

    @Test
    void noTokenShouldReachTheLog(CapturedOutput output) throws Exception {
        var first = loginAndGetTheRefreshToken();
        var second = refreshTokenIn(refresh(first).andReturn().getResponse());
        refresh(first);
        logout(second);

        assertThat(output.getAll()).doesNotContain(first).doesNotContain(second).doesNotContain(PASSWORD);
    }
}
