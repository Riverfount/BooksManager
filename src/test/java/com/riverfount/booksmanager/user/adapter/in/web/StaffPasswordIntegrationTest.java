package com.riverfount.booksmanager.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import java.time.Instant;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The staff changes their own password (RF09) through the whole stack and with no test transaction, so what is
 * committed is what is read back. It proves what a rolled-back test transaction would hide: the count of wrong
 * current passwords survives the exception that refuses them (otherwise nobody would ever be locked out through this
 * route), the lockout holds even for the right password, and the new password, the revoked refresh tokens and the audit
 * entries are really stored.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class StaffPasswordIntegrationTest {

    private static final String OPERATOR = "troca-operador";
    private static final String ADMIN = "troca-admin";
    private static final String OLD_PASSWORD = "Antiga#Senha2025";
    private static final String NEW_PASSWORD = "Nova#Senha2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private Long operatorId;
    private Long adminId;

    @BeforeEach
    void createTheUsers() {
        cleanUp();
        operatorId = insert(OPERATOR, "OPERATOR", true);
        adminId = insert(ADMIN, "ADMIN", true);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from audit_log where target_id in (select id from app_user where login in (?, ?))",
                OPERATOR, ADMIN);
        jdbcTemplate.update("delete from refresh_token where user_id in (select id from app_user where login in (?, ?))",
                OPERATOR, ADMIN);
        jdbcTemplate.update("delete from app_user where login in (?, ?)", OPERATOR, ADMIN);
    }

    private Long insert(String login, String role, boolean active) {
        return jdbcTemplate.queryForObject(
                "insert into app_user (login, password_hash, role, active) values (?, ?, ?, ?) returning id", Long.class,
                login, passwordEncoder.encode(OLD_PASSWORD), role, active);
    }

    private String token(Long userId, String role) {
        return "Bearer " + TestTokens.signed(encoder, properties.issuer(), String.valueOf(userId), role, Instant.now(),
                Instant.now().plusSeconds(900));
    }

    private ResultActions change(Long userId, String role, String current, String next) throws Exception {
        return mockMvc.perform(post("/api/v1/users/me/password").header("Authorization", token(userId, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    private ResultActions login(String login, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"));
    }

    private int failedAttempts(String login) {
        return jdbcTemplate.queryForObject("select failed_login_attempts from app_user where login = ?", Integer.class,
                login);
    }

    private String hashOf(String login) {
        return jdbcTemplate.queryForObject("select password_hash from app_user where login = ?", String.class, login);
    }

    private long activeRefreshTokens(Long userId) {
        return jdbcTemplate.queryForObject("select count(*) from refresh_token where user_id = ? and revoked_at is null",
                Long.class, userId);
    }

    @Test
    void anOperatorShouldChangeThePasswordAndLogInWithTheNewOneOnly() throws Exception {
        change(operatorId, "OPERATOR", OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        login(OPERATOR, NEW_PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
        login(OPERATOR, OLD_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void anAdministratorShouldChangeTheirOwnPasswordToo() throws Exception {
        change(adminId, "ADMIN", OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        login(ADMIN, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void theNewPasswordShouldBeStoredAsABcryptHashNeverInThePlainText() throws Exception {
        change(operatorId, "OPERATOR", OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(hashOf(OPERATOR)).startsWith("$2").doesNotContain(NEW_PASSWORD);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, hashOf(OPERATOR))).isTrue();
    }

    @Test
    void shouldEndEveryRefreshTokenOfTheUserAndOnlyOfHim() throws Exception {
        login(OPERATOR, OLD_PASSWORD).andExpect(status().isOk());
        login(ADMIN, OLD_PASSWORD).andExpect(status().isOk());
        assertThat(activeRefreshTokens(operatorId)).isEqualTo(1);

        change(operatorId, "OPERATOR", OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(activeRefreshTokens(operatorId)).isZero();
        assertThat(activeRefreshTokens(adminId)).isEqualTo(1);
    }

    @Test
    void aWrongCurrentPasswordGets422AndTheCountSurvivesTheRefusal() throws Exception {
        change(operatorId, "OPERATOR", "Errada#Senha1", NEW_PASSWORD).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("a senha atual está incorreta"));
        change(operatorId, "OPERATOR", "Errada#Senha2", NEW_PASSWORD).andExpect(status().isUnprocessableContent());

        assertThat(failedAttempts(OPERATOR)).isEqualTo(2);
        login(OPERATOR, OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void tooManyWrongCurrentPasswordsLockTheAccountAndTheRouteIsNoWayAroundIt() throws Exception {
        for (var attempt = 1; attempt <= 3; attempt++) {
            change(operatorId, "OPERATOR", "Errada#Senha" + attempt, NEW_PASSWORD)
                    .andExpect(status().isUnprocessableContent());
        }
        assertThat(jdbcTemplate.queryForObject("select blocked_until is not null from app_user where login = ?",
                Boolean.class, OPERATOR)).isTrue();

        change(operatorId, "OPERATOR", OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("bloqueada")));

        login(OPERATOR, OLD_PASSWORD).andExpect(status().is4xxClientError());
        assertThat(passwordEncoder.matches(OLD_PASSWORD, hashOf(OPERATOR))).isTrue();
    }

    @Test
    void aWeakNewPasswordGets422SayingWhatIsMissingAndChangesNothing() throws Exception {
        change(operatorId, "OPERATOR", OLD_PASSWORD, "fraca").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("a senha não atende às regras")));

        assertThat(passwordEncoder.matches(OLD_PASSWORD, hashOf(OPERATOR))).isTrue();
        assertThat(failedAttempts(OPERATOR)).isZero();
    }

    @Test
    void shouldReportTheChangeWithTheUserAsActorAndTargetAndWithoutAnySecret() throws Exception {
        change(operatorId, "OPERATOR", OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        var rows = jdbcTemplate.queryForList(
                "select * from audit_log where action = 'USER_PASSWORD_CHANGED' and target_id = ?", operatorId);
        assertThat(rows).hasSize(1);
        var entry = rows.getFirst();
        assertThat(entry.get("actor_id")).isEqualTo(operatorId);
        assertThat(entry.get("actor_login")).isEqualTo(OPERATOR);
        assertThat(entry.get("target_type")).isEqualTo("USER");
        assertThat(entry.get("summary")).isNull();
        assertThat(entry.toString()).doesNotContain(OLD_PASSWORD).doesNotContain(NEW_PASSWORD)
                .doesNotContain(hashOf(OPERATOR));
    }

    @Test
    void aWrongCurrentPasswordIsReportedAsALoginFailureWithoutTheValue() throws Exception {
        change(operatorId, "OPERATOR", "Errada#Senha1", NEW_PASSWORD).andExpect(status().isUnprocessableContent());

        var rows = jdbcTemplate.queryForList(
                "select * from audit_log where action = 'LOGIN_FAILURE' and target_id = ?", operatorId);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("summary")).isEqualTo("senha atual incorreta");
        assertThat(rows.getFirst().toString()).doesNotContain("Errada#Senha1");
    }

    @Test
    void anInactiveUserIsRefusedAndNothingChanges() throws Exception {
        jdbcTemplate.update("update app_user set active = false where login = ?", OPERATOR);

        change(operatorId, "OPERATOR", OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isUnprocessableContent());

        assertThat(passwordEncoder.matches(OLD_PASSWORD, hashOf(OPERATOR))).isTrue();
    }

    @Test
    void aReaderTokenGets403AndNoTokenGets401() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/password").header("Authorization", token(operatorId, "READER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + OLD_PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/users/me/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + OLD_PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(passwordEncoder.matches(OLD_PASSWORD, hashOf(OPERATOR))).isTrue();
    }

    @Test
    void theUserIsAlwaysTheOneOfTheTokenNeverAnotherOne() throws Exception {
        change(operatorId, "OPERATOR", OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(passwordEncoder.matches(OLD_PASSWORD, hashOf(ADMIN))).isTrue();
    }
}
