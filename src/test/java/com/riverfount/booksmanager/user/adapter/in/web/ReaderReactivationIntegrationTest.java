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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reactivating a reader (RF07, issue #367) with the real chain and PostgreSQL: an inactivated reader comes back, a
 * reader that was blocked comes back blocked, the access revoked at the inactivation is not given back, the audit trail
 * gets the fact with the caller as actor, and only an administrator may do it. Each test runs in a transaction that is
 * rolled back.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class ReaderReactivationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    private Long chefeId;
    private Long readerId;

    @BeforeEach
    void setUp() {
        chefeId = jdbcTemplate.queryForObject(
                "insert into app_user (login, password_hash, role, active) values ('chefe', ?, 'ADMIN', true) returning id",
                Long.class, new BCryptPasswordEncoder().encode("S3cret-Pass!"));
        readerId = jdbcTemplate.queryForObject("insert into reader (name, document, email, birth_date) "
                + "values ('Ana', '12345678901', 'ana@example.com', '1990-01-01') returning id", Long.class);
    }

    private String token(String role) {
        return "Bearer " + TestTokens.signed(encoder, properties.issuer(), String.valueOf(chefeId), role,
                Instant.now(), Instant.now().plusSeconds(900));
    }

    private ResultActions reactivate(String role) throws Exception {
        return mockMvc.perform(post("/api/v1/readers/" + readerId + "/reactivate").header("Authorization", token(role)));
    }

    private ResultActions inactivate() throws Exception {
        return mockMvc.perform(delete("/api/v1/readers/" + readerId).header("Authorization", token("ADMIN")));
    }

    private boolean readerIsActive() {
        return jdbcTemplate.queryForObject("select active from reader where id = ?", Boolean.class, readerId);
    }

    private long reactivations() {
        return jdbcTemplate.queryForObject(
                "select count(*) from audit_log where action = 'READER_REACTIVATED' and target_id = ?", Long.class,
                readerId);
    }

    @Test
    void anAdministratorShouldReactivateAnInactivatedReader() throws Exception {
        inactivate().andExpect(status().isNoContent());
        assertThat(readerIsActive()).isFalse();

        reactivate("ADMIN").andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(readerId))
                .andExpect(jsonPath("$.active").value(true));

        assertThat(readerIsActive()).isTrue();
    }

    @Test
    void aReaderThatWasBlockedComesBackBlocked() throws Exception {
        jdbcTemplate.update("update reader set blocked = true where id = ?", readerId);
        inactivate().andExpect(status().isNoContent());

        reactivate("ADMIN").andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.blocked").value(true));
    }

    @Test
    void theAccessRevokedAtTheInactivationIsNotGivenBack() throws Exception {
        jdbcTemplate.update("insert into app_user (login, password_hash, role, active, reader_id) "
                + "values ('ana', 'x', 'READER', true, ?)", readerId);
        inactivate().andExpect(status().isNoContent());

        reactivate("ADMIN").andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("select active from app_user where login = 'ana'", Boolean.class))
                .isFalse();
    }

    @Test
    void reactivatingAnActiveReaderIsA200ThatAuditsNothing() throws Exception {
        reactivate("ADMIN").andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));

        assertThat(reactivations()).isZero();
    }

    @Test
    void shouldReportTheReactivationOnceToTheAuditTrailWithTheCallerAndNoPersonalData() throws Exception {
        inactivate().andExpect(status().isNoContent());

        reactivate("ADMIN").andExpect(status().isOk());
        reactivate("ADMIN").andExpect(status().isOk());

        var rows = jdbcTemplate.queryForList(
                "select * from audit_log where action = 'READER_REACTIVATED' and target_id = ?", readerId);
        assertThat(rows).hasSize(1);
        var entry = rows.getFirst();
        assertThat(entry.get("target_type")).isEqualTo("READER");
        assertThat(entry.get("actor_id")).isEqualTo(chefeId);
        assertThat(entry.get("actor_login")).isEqualTo("chefe");
        assertThat(entry.get("summary")).isNull();
        assertThat(entry.toString()).doesNotContain("Ana").doesNotContain("12345678901")
                .doesNotContain("ana@example.com");
    }

    @Test
    void anUnknownReaderGets404() throws Exception {
        mockMvc.perform(post("/api/v1/readers/999999/reactivate").header("Authorization", token("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void anOperatorAndAnUnknownProfileGet403AndNoTokenGets401AndNothingChanges() throws Exception {
        inactivate().andExpect(status().isNoContent());

        for (var role : new String[] {"OPERATOR", "SUPERUSER"}) {
            reactivate(role).andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/v1/readers/" + readerId + "/reactivate")).andExpect(status().isUnauthorized());

        assertThat(readerIsActive()).isFalse();
    }
}
