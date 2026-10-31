package com.riverfount.booksmanager.user.adapter.in.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.user.application.port.in.ResetAdminPasswordUseCase;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The recovery of an administrator who forgot the password (RF08, RF09), through the whole stack and with no test
 * transaction, so what is committed is what is read back: the new password logs in and the forgotten one does not, the
 * login block and the refresh tokens are gone, an operator is never touched, the audit trail has the fact with no
 * actor and no secret, and running it again changes nothing.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ExtendWith(OutputCaptureExtension.class)
class AdminPasswordResetIntegrationTest {

    private static final String ADMIN = "recovery-admin";
    private static final String OPERATOR = "recovery-operator";
    private static final String OLD_PASSWORD = "Esquecida#2025";
    private static final String NEW_PASSWORD = "Recuperada#2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ResetAdminPasswordUseCase useCase;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void createTheUsers() {
        cleanUp();
        insert(ADMIN, "ADMIN", OLD_PASSWORD);
        insert(OPERATOR, "OPERATOR", OLD_PASSWORD);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from audit_log where target_id in (select id from app_user where login in (?, ?))",
                ADMIN, OPERATOR);
        jdbcTemplate.update("delete from refresh_token where user_id in (select id from app_user where login in (?, ?))",
                ADMIN, OPERATOR);
        jdbcTemplate.update("delete from app_user where login in (?, ?)", ADMIN, OPERATOR);
    }

    private void insert(String login, String role, String password) {
        jdbcTemplate.update("insert into app_user (login, password_hash, role) values (?, ?, ?)", login,
                encoder.encode(password), role);
    }

    private long idOf(String login) {
        return jdbcTemplate.queryForObject("select id from app_user where login = ?", Long.class, login);
    }

    private String hashOf(String login) {
        return jdbcTemplate.queryForObject("select password_hash from app_user where login = ?", String.class, login);
    }

    private ResultActions login(String login, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"));
    }

    private void runTheRecovery(String login, String password) {
        new AdminPasswordResetRunner(useCase, new AdminProperties(login, "irrelevante", password)).run(null);
    }

    private long activeRefreshTokens(String login) {
        return jdbcTemplate.queryForObject("select count(*) from refresh_token where user_id = ? and revoked_at is null",
                Long.class, idOf(login));
    }

    @Test
    void theNewPasswordShouldLogInAndTheForgottenOneShouldNot() throws Exception {
        login(ADMIN, NEW_PASSWORD).andExpect(status().isUnauthorized());

        runTheRecovery(ADMIN, NEW_PASSWORD);

        login(ADMIN, NEW_PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
        login(ADMIN, OLD_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldClearTheLoginBlockSoTheAdministratorCanLogInAtOnce() throws Exception {
        jdbcTemplate.update("update app_user set failed_login_attempts = 3, blocked_until = now() + interval '15 minutes' "
                + "where login = ?", ADMIN);
        login(ADMIN, OLD_PASSWORD).andExpect(status().is4xxClientError());

        runTheRecovery(ADMIN, NEW_PASSWORD);

        var row = jdbcTemplate.queryForMap("select failed_login_attempts, blocked_until from app_user where login = ?",
                ADMIN);
        assertThat(row.get("failed_login_attempts")).isEqualTo(0);
        assertThat(row.get("blocked_until")).isNull();
        login(ADMIN, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void shouldRevokeTheRefreshTokensOfTheAdministratorAndOnlyOfHim() throws Exception {
        login(ADMIN, OLD_PASSWORD).andExpect(status().isOk());
        login(OPERATOR, OLD_PASSWORD).andExpect(status().isOk());
        assertThat(activeRefreshTokens(ADMIN)).isEqualTo(1);

        runTheRecovery(ADMIN, NEW_PASSWORD);

        assertThat(activeRefreshTokens(ADMIN)).isZero();
        assertThat(activeRefreshTokens(OPERATOR)).isEqualTo(1);
    }

    @Test
    void shouldNeverTouchAnOperatorEvenWhenItsLoginIsGiven() throws Exception {
        var before = hashOf(OPERATOR);

        runTheRecovery(OPERATOR, NEW_PASSWORD);

        assertThat(hashOf(OPERATOR)).isEqualTo(before);
        login(OPERATOR, OLD_PASSWORD).andExpect(status().isOk());
        login(OPERATOR, NEW_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReportTheResetToTheAuditTrailWithoutActorAndWithoutAnySecret() {
        runTheRecovery(ADMIN, NEW_PASSWORD);

        var rows = jdbcTemplate.queryForList("select * from audit_log where target_id = ?", idOf(ADMIN));
        assertThat(rows).hasSize(1);
        var entry = rows.getFirst();
        assertThat(entry.get("action")).isEqualTo("USER_PASSWORD_RESET");
        assertThat(entry.get("target_type")).isEqualTo("USER");
        assertThat(entry.get("actor_id")).isNull();
        assertThat(entry.get("summary")).isEqualTo("senha redefinida na subida, pelo ambiente");
        assertThat(entry.toString()).doesNotContain(NEW_PASSWORD).doesNotContain(OLD_PASSWORD)
                .doesNotContain(hashOf(ADMIN));
    }

    @Test
    void runningItAgainShouldChangeNothing() {
        runTheRecovery(ADMIN, NEW_PASSWORD);
        var hash = hashOf(ADMIN);

        runTheRecovery(ADMIN, NEW_PASSWORD);
        runTheRecovery(ADMIN, NEW_PASSWORD);

        assertThat(hashOf(ADMIN)).isEqualTo(hash);
        assertThat(jdbcTemplate.queryForObject("select count(*) from audit_log where target_id = ?", Long.class,
                idOf(ADMIN))).isEqualTo(1L);
    }

    @Test
    void aWeakPasswordShouldChangeNothingAndNeverStopTheStartup(CapturedOutput output) throws Exception {
        var before = hashOf(ADMIN);

        runTheRecovery(ADMIN, "curta");

        assertThat(hashOf(ADMIN)).isEqualTo(before);
        login(ADMIN, OLD_PASSWORD).andExpect(status().isOk());
        assertThat(output.getAll()).contains("was not applied").doesNotContain("curta\"");
    }

    @Test
    void anInactiveAdministratorShouldStayExactlyAsHeIs() {
        jdbcTemplate.update("update app_user set active = false where login = ?", ADMIN);
        var before = hashOf(ADMIN);

        runTheRecovery(ADMIN, NEW_PASSWORD);

        assertThat(hashOf(ADMIN)).isEqualTo(before);
        assertThat(jdbcTemplate.queryForObject("select active from app_user where login = ?", Boolean.class, ADMIN))
                .isFalse();
    }
}
