package com.riverfount.booksmanager.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Resetting the password of a system user (RF09, HU29, RNF07) with the real chain
 * and PostgreSQL: the new password is stored as a BCrypt hash, the old one stops
 * working and every refresh token of the user is revoked. Each test runs in a
 * transaction that is rolled back.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class UserPasswordResetIntegrationTest {

    private static final String PASSWORD = "S3cret-Pass!";
    private static final String NEW_PASSWORD = "N3w-Pass#word";

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

    private String token(Long userId, String role) {
        return "Bearer " + TestTokens.signed(encoder, properties.issuer(), String.valueOf(userId), role,
                Instant.now(), Instant.now().plusSeconds(900));
    }

    private ResultActions reset(Long actingUserId, String role, Object id, String password) throws Exception {
        return mockMvc.perform(put("/api/v1/users/" + id + "/password")
                .header("Authorization", token(actingUserId, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + password + "\"}"));
    }

    private ResultActions login(String login, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"));
    }

    private String hashOf(Long id) {
        return jdbcTemplate.queryForObject("select password_hash from app_user where id = ?", String.class, id);
    }

    private static String refreshTokenIn(String setCookie) {
        return setCookie.substring("refresh_token=".length(), setCookie.indexOf(';'));
    }

    @Test
    void shouldStoreTheNewPasswordAsABCryptHashAndNeverTheText() throws Exception {
        var oldHash = hashOf(mariaId);

        var response = reset(chefeId, "ADMIN", mariaId, NEW_PASSWORD).andExpect(status().isNoContent())
                .andReturn().getResponse();

        assertThat(response.getContentAsString()).isEmpty();
        assertThat(hashOf(mariaId)).isNotEqualTo(oldHash).startsWith("$2").doesNotContain(NEW_PASSWORD);
        assertThat(new BCryptPasswordEncoder().matches(NEW_PASSWORD, hashOf(mariaId))).isTrue();
    }

    @Test
    void theOldPasswordStopsWorkingAndTheNewOneLogsIn() throws Exception {
        login("maria", PASSWORD).andExpect(status().isOk());

        reset(chefeId, "ADMIN", mariaId, NEW_PASSWORD).andExpect(status().isNoContent());

        login("maria", PASSWORD).andExpect(status().isUnauthorized());
        login("maria", NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void everyRefreshTokenOfTheUserIsRevokedAndNobodyElsesIs() throws Exception {
        var first = refreshTokenIn(login("maria", PASSWORD).andExpect(status().isOk()).andReturn().getResponse()
                .getHeader("Set-Cookie"));
        var second = refreshTokenIn(login("maria", PASSWORD).andReturn().getResponse().getHeader("Set-Cookie"));
        var others = refreshTokenIn(login("chefe", PASSWORD).andExpect(status().isOk()).andReturn().getResponse()
                .getHeader("Set-Cookie"));

        reset(chefeId, "ADMIN", mariaId, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from refresh_token where user_id = ? and revoked_at is null", Integer.class, mariaId))
                .isZero();
        for (var token : new String[] {first, second}) {
            mockMvc.perform(post("/api/v1/auth/refresh").header("X-Requested-With", "XMLHttpRequest")
                            .cookie(new Cookie("refresh_token", token)))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/refresh").header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("refresh_token", others)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRefuseAWeakPasswordWith422KeepingTheCurrentOneAndTheTokens() throws Exception {
        login("maria", PASSWORD).andExpect(status().isOk());
        var oldHash = hashOf(mariaId);

        var body = reset(chefeId, "ADMIN", mariaId, "weakpassword").andExpect(status().isUnprocessableContent())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("senha").doesNotContain("weakpassword");
        assertThat(hashOf(mariaId)).isEqualTo(oldHash);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from refresh_token where user_id = ? and revoked_at is null", Integer.class, mariaId))
                .isEqualTo(1);
    }

    @Test
    void shouldAllowAnAdministratorToResetTheirOwnPassword() throws Exception {
        reset(chefeId, "ADMIN", chefeId, NEW_PASSWORD).andExpect(status().isNoContent());

        login("chefe", NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void shouldReturn404ForAnUnknownUserAnd400ForANonNumericIdOrAMissingPassword() throws Exception {
        reset(chefeId, "ADMIN", 999_999L, NEW_PASSWORD).andExpect(status().isNotFound());
        reset(chefeId, "ADMIN", "abc", NEW_PASSWORD).andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/users/" + mariaId + "/password")
                        .header("Authorization", token(chefeId, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anOperatorAndAnUnknownProfileGet403AndNoTokenGets401AndNothingChanges() throws Exception {
        var oldHash = hashOf(mariaId);

        reset(mariaId, "OPERATOR", mariaId, NEW_PASSWORD).andExpect(status().isForbidden());
        reset(mariaId, "SUPERUSER", mariaId, NEW_PASSWORD).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/users/" + mariaId + "/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + NEW_PASSWORD + "\"}")).andExpect(status().isUnauthorized());

        assertThat(hashOf(mariaId)).isEqualTo(oldHash);
    }
}
