package com.riverfount.booksmanager.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reactivating a staff user (RF09) with the real chain and PostgreSQL: an inactivated operator or administrator comes
 * back with the same password, the refresh tokens revoked at the inactivation stay revoked, a reader access is refused,
 * the audit trail gets the fact with the caller as actor, and only an administrator may do it. Each test runs in a
 * transaction that is rolled back.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class UserReactivationIntegrationTest {

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
        chefeId = insertUser("chefe", "ADMIN", true, null);
        mariaId = insertUser("maria", "OPERATOR", true, null);
    }

    private Long insertUser(String login, String role, boolean active, Long readerId) {
        return jdbcTemplate.queryForObject(
                "insert into app_user (login, password_hash, role, active, reader_id) values (?, ?, ?, ?, ?) returning id",
                Long.class, login, new BCryptPasswordEncoder().encode(PASSWORD), role, active, readerId);
    }

    private String token(String role, Long subject) {
        return "Bearer " + TestTokens.signed(encoder, properties.issuer(), String.valueOf(subject), role,
                Instant.now(), Instant.now().plusSeconds(900));
    }

    private ResultActions reactivate(String role, Long caller, Long id) throws Exception {
        return mockMvc.perform(post("/api/v1/users/" + id + "/reactivate").header("Authorization", token(role, caller)));
    }

    private ResultActions inactivate(Long id) throws Exception {
        return mockMvc.perform(delete("/api/v1/users/" + id).header("Authorization", token("ADMIN", chefeId)));
    }

    private ResultActions login(String login, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"));
    }

    private boolean isActive(String login) {
        return jdbcTemplate.queryForObject("select active from app_user where login = ?", Boolean.class, login);
    }

    private long activeRefreshTokens(Long userId) {
        return jdbcTemplate.queryForObject("select count(*) from refresh_token where user_id = ? and revoked_at is null",
                Long.class, userId);
    }

    private long reactivations(Long userId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from audit_log where action = 'USER_REACTIVATED' and target_id = ?", Long.class, userId);
    }

    @Test
    void anAdministratorShouldReactivateAnInactivatedOperatorWhoLogsInAgainWithTheSamePassword() throws Exception {
        inactivate(mariaId).andExpect(status().isNoContent());
        login("maria", PASSWORD).andExpect(status().isUnauthorized());

        reactivate("ADMIN", chefeId, mariaId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("maria"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.role").value("OPERATOR"));

        assertThat(isActive("maria")).isTrue();
        login("maria", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void shouldReactivateAnInactiveAdministrator() throws Exception {
        var outroId = insertUser("outro", "ADMIN", false, null);

        reactivate("ADMIN", chefeId, outroId).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));

        login("outro", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void theRefreshTokensRevokedAtTheInactivationStayRevoked() throws Exception {
        login("maria", PASSWORD).andExpect(status().isOk());
        assertThat(activeRefreshTokens(mariaId)).isEqualTo(1);
        inactivate(mariaId).andExpect(status().isNoContent());
        assertThat(activeRefreshTokens(mariaId)).isZero();

        reactivate("ADMIN", chefeId, mariaId).andExpect(status().isOk());

        assertThat(activeRefreshTokens(mariaId)).isZero();
    }

    @Test
    void reactivatingAnActiveUserIsA200ThatChangesAndAuditsNothing() throws Exception {
        reactivate("ADMIN", chefeId, mariaId).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        reactivate("ADMIN", chefeId, mariaId).andExpect(status().isOk());

        assertThat(reactivations(mariaId)).isZero();
    }

    @Test
    void shouldReportTheReactivationToTheAuditTrailWithTheCallerAndNoSecret() throws Exception {
        inactivate(mariaId).andExpect(status().isNoContent());

        reactivate("ADMIN", chefeId, mariaId).andExpect(status().isOk());

        var rows = jdbcTemplate.queryForList(
                "select * from audit_log where action = 'USER_REACTIVATED' and target_id = ?", mariaId);
        assertThat(rows).hasSize(1);
        var entry = rows.getFirst();
        assertThat(entry.get("target_type")).isEqualTo("USER");
        assertThat(entry.get("actor_id")).isEqualTo(chefeId);
        assertThat(entry.get("actor_login")).isEqualTo("chefe");
        assertThat(entry.get("summary")).isNull();
        assertThat(entry.toString()).doesNotContain(PASSWORD);
    }

    @Test
    void aReaderAccessIsRefusedWith422AndStaysInactive() throws Exception {
        var readerId = jdbcTemplate.queryForObject("insert into reader (name, document, email, birth_date) "
                + "values ('Ana', '12345678901', 'ana@example.com', '1990-01-01') returning id", Long.class);
        var accessId = insertUser("ana", "READER", false, readerId);

        reactivate("ADMIN", chefeId, accessId).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("o acesso do leitor é reaberto por um novo convite"));

        assertThat(isActive("ana")).isFalse();
        assertThat(reactivations(accessId)).isZero();
    }

    @Test
    void anUnknownUserGets404() throws Exception {
        reactivate("ADMIN", chefeId, 999_999L).andExpect(status().isNotFound());
    }

    @Test
    void anOperatorAndAnUnknownProfileGet403AndNoTokenGets401AndNothingChanges() throws Exception {
        inactivate(mariaId).andExpect(status().isNoContent());

        for (var role : new String[] {"OPERATOR", "SUPERUSER"}) {
            reactivate(role, chefeId, mariaId).andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/v1/users/" + mariaId + "/reactivate")).andExpect(status().isUnauthorized());

        assertThat(isActive("maria")).isFalse();
    }
}
