package com.riverfount.booksmanager.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import com.riverfount.booksmanager.user.application.port.out.PasswordHasher;
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
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The reader maintains their own registration (RF32, RN22) through the real path: security chain, transactions and
 * PostgreSQL, with no test transaction, so a rollback is a rollback. Two readers prove that nobody edits another
 * reader's data, and the wrong password proves that the attempt that is refused is still counted (it has to survive
 * the exception that refuses it).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class OwnProfileIntegrationTest {

    private static final String PASSWORD = "Rd-Pr0f1le#Pass";
    private static final String NEW_PASSWORD = "N3w-Pr0f#Passw0rd";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    private long anaId;
    private long beaId;
    private long anaUserId;

    @BeforeEach
    void createTwoReadersWithAccess() {
        cleanUp();
        anaId = newReader("OPRF-DOC-1", "Ana Souza", "oprf-ana@example.com", "11 91111-1111");
        beaId = newReader("OPRF-DOC-2", "Bea Lima", "oprf-bea@example.com", "11 92222-2222");
        anaUserId = newAccess("oprf-ana", anaId);
        newAccess("oprf-bea", beaId);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from audit_log");
        jdbcTemplate.update("delete from refresh_token where user_id in (select id from app_user where login like 'oprf-%')");
        jdbcTemplate.update("delete from app_user where login like 'oprf-%'");
        jdbcTemplate.update("delete from reader where document like 'OPRF-DOC-%'");
    }

    private long newReader(String document, String name, String email, String phone) {
        return jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, phone, birth_date)
                values (?, ?, ?, ?, date '1990-01-01') returning id
                """, Long.class, name, document, email, phone);
    }

    private long newAccess(String login, long readerId) {
        return jdbcTemplate.queryForObject("""
                insert into app_user (login, password_hash, role, reader_id) values (?, ?, 'READER', ?) returning id
                """, Long.class, login, passwordHasher.hash(PASSWORD), readerId);
    }

    private ResultActions login(String login, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"));
    }

    private String bearer(String login) throws Exception {
        var body = login(login, PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    private ResultActions patchProfile(String authorization, String json) throws Exception {
        return mockMvc.perform(patch("/api/v1/me/profile").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions changePassword(String authorization, String current, String next) throws Exception {
        return mockMvc.perform(post("/api/v1/me/password").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    private String field(String table, long id, String column) {
        return jdbcTemplate.queryForObject("select " + column + " from " + table + " where id = ?", String.class, id);
    }

    @Test
    void shouldShowTheOwnRegistrationOnly() throws Exception {
        mockMvc.perform(get("/api/v1/me/profile").header("Authorization", bearer("oprf-ana")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana Souza"))
                .andExpect(jsonPath("$.email").value("oprf-ana@example.com"))
                .andExpect(jsonPath("$.document").value("********-1"))
                .andExpect(jsonPath("$.minor").value(false));
    }

    @Test
    void shouldChangeTheOwnPhoneAndLeaveTheOtherReaderUntouched() throws Exception {
        patchProfile(bearer("oprf-ana"), "{\"phone\":\"21 98888-7777\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("21 98888-7777"));

        assertThat(field("reader", anaId, "phone")).isEqualTo("21 98888-7777");
        assertThat(field("reader", beaId, "phone")).isEqualTo("11 92222-2222");
    }

    @Test
    void shouldIgnoreTheReaderIdAndTheIdentityFieldsSentInTheBody() throws Exception {
        patchProfile(bearer("oprf-ana"), "{\"phone\":\"21 98888-7777\",\"readerId\":" + beaId
                + ",\"name\":\"Invasora\",\"document\":\"OPRF-DOC-9\",\"birthDate\":\"2010-01-01\","
                + "\"blocked\":true,\"active\":false}").andExpect(status().isOk());

        assertThat(field("reader", beaId, "phone")).isEqualTo("11 92222-2222");
        assertThat(field("reader", beaId, "name")).isEqualTo("Bea Lima");
        assertThat(field("reader", anaId, "name")).isEqualTo("Ana Souza");
        assertThat(field("reader", anaId, "document")).isEqualTo("OPRF-DOC-1");
        assertThat(field("reader", anaId, "birth_date")).isEqualTo("1990-01-01");
        assertThat(field("reader", anaId, "blocked")).isEqualTo("f");
        assertThat(field("reader", anaId, "active")).isEqualTo("t");
    }

    @Test
    void shouldLeaveOnlyTheNamesOfTheChangedFieldsInTheAuditTrail() throws Exception {
        patchProfile(bearer("oprf-ana"), "{\"phone\":\"21 98888-7777\"}").andExpect(status().isOk());

        var entries = jdbcTemplate.queryForList("select * from audit_log where action = 'READER_PROFILE_UPDATED'");
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().get("actor_id")).isEqualTo(anaUserId);
        assertThat(entries.getFirst().get("target_id")).isEqualTo(anaId);
        assertThat(entries.getFirst().get("summary")).isEqualTo("campos: telefone");
        assertThat(entries.getFirst().toString()).doesNotContain("98888").doesNotContain("oprf-ana@example.com");
    }

    @Test
    void shouldRefuseTheEmailChangeWithoutThePasswordAndStillCountTheAttempt() throws Exception {
        patchProfile(bearer("oprf-ana"), "{\"email\":\"novo@example.com\",\"currentPassword\":\"errada\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("a senha atual está incorreta"));

        assertThat(field("reader", anaId, "email")).isEqualTo("oprf-ana@example.com");
        // the refusal did not roll the count back: the wrong attempt is on record, in the account and in the trail
        assertThat(field("app_user", anaUserId, "failed_login_attempts")).isEqualTo("1");
        assertThat(jdbcTemplate.queryForList("select 1 from audit_log where action = 'LOGIN_FAILURE' "
                + "and summary = 'senha atual incorreta'")).hasSize(1);
    }

    @Test
    void shouldChangeTheEmailWithThePasswordKeepingTheLogin() throws Exception {
        patchProfile(bearer("oprf-ana"), "{\"email\":\"novo@example.com\",\"currentPassword\":\"" + PASSWORD + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("novo@example.com"));

        assertThat(field("reader", anaId, "email")).isEqualTo("novo@example.com");
        assertThat(field("app_user", anaUserId, "login")).isEqualTo("oprf-ana");
        login("oprf-ana", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void shouldLockTheAccountAfterTooManyWrongPasswordsThroughThisRouteToo() throws Exception {
        var authorization = bearer("oprf-ana");
        for (var attempt = 0; attempt < 5; attempt++) {
            patchProfile(authorization, "{\"email\":\"novo@example.com\",\"currentPassword\":\"errada\"}")
                    .andExpect(status().isUnprocessableContent());
        }

        login("oprf-ana", PASSWORD).andExpect(status().isUnauthorized());
        patchProfile(authorization, "{\"email\":\"novo@example.com\",\"currentPassword\":\"" + PASSWORD + "\"}")
                .andExpect(status().isUnprocessableContent());
        assertThat(field("reader", anaId, "email")).isEqualTo("oprf-ana@example.com");
    }

    @Test
    void shouldChangeThePasswordAndEndTheSessions() throws Exception {
        var authorization = bearer("oprf-ana");
        assertThat(jdbcTemplate.queryForObject("select count(*) from refresh_token where user_id = ? "
                + "and revoked_at is null", Long.class, anaUserId)).isEqualTo(1L);

        changePassword(authorization, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("select count(*) from refresh_token where user_id = ? "
                + "and revoked_at is null", Long.class, anaUserId)).isZero();
        login("oprf-ana", PASSWORD).andExpect(status().isUnauthorized());
        login("oprf-ana", NEW_PASSWORD).andExpect(status().isOk());
        assertThat(field("app_user", anaUserId, "password_hash")).startsWith("$2").doesNotContain(NEW_PASSWORD);
        var entry = jdbcTemplate.queryForMap("select * from audit_log where action = 'READER_PASSWORD_CHANGED'");
        assertThat(entry.get("actor_id")).isEqualTo(anaUserId);
        assertThat(entry.toString()).doesNotContain(PASSWORD).doesNotContain(NEW_PASSWORD).doesNotContain("$2");
    }

    @Test
    void shouldNotTouchTheOtherReaderPasswordNorSessions() throws Exception {
        bearer("oprf-bea");
        var beaHash = field("app_user", anaUserId + 1, "password_hash");

        changePassword(bearer("oprf-ana"), PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(field("app_user", anaUserId + 1, "password_hash")).isEqualTo(beaHash);
        login("oprf-bea", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void shouldRefuseAWrongCurrentPasswordAndKeepTheOldOne() throws Exception {
        changePassword(bearer("oprf-ana"), "errada", NEW_PASSWORD).andExpect(status().isUnprocessableContent());

        login("oprf-ana", PASSWORD).andExpect(status().isOk());
        assertThat(field("app_user", anaUserId, "failed_login_attempts")).isEqualTo("0");
    }

    @Test
    void shouldRefuseAWeakNewPassword() throws Exception {
        changePassword(bearer("oprf-ana"), PASSWORD, "fraca").andExpect(status().isUnprocessableContent());

        login("oprf-ana", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void shouldKeepTheStaffOutOfTheReaderRoutes() throws Exception {
        var operatorId = jdbcTemplate.queryForObject("""
                insert into app_user (login, password_hash, role) values ('oprf-operator', ?, 'OPERATOR') returning id
                """, Long.class, passwordHasher.hash(PASSWORD));
        var operator = "Bearer " + TestTokens.signed(encoder, properties.issuer(), String.valueOf(operatorId),
                "OPERATOR", Instant.now(), Instant.now().plusSeconds(900));

        mockMvc.perform(get("/api/v1/me/profile").header("Authorization", operator)).andExpect(status().isForbidden());
        patchProfile(operator, "{\"phone\":\"21 98888-7777\"}").andExpect(status().isForbidden());
        changePassword(operator, PASSWORD, NEW_PASSWORD).andExpect(status().isForbidden());
    }

    @Test
    void shouldKeepTheReaderOutOfTheStaffRouteThatEditsReaders() throws Exception {
        mockMvc.perform(patch("/api/v1/readers/" + anaId).header("Authorization", bearer("oprf-ana"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Outro Nome\"}"))
                .andExpect(status().isForbidden());

        assertThat(field("reader", anaId, "name")).isEqualTo("Ana Souza");
    }
}
