package com.riverfount.booksmanager.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import com.riverfount.booksmanager.user.application.port.out.PasswordHasher;
import com.riverfount.booksmanager.user.application.port.out.ReaderAccessTokenRepository;
import com.riverfount.booksmanager.user.domain.ReaderAccessToken;
import com.riverfount.booksmanager.user.domain.ReaderAccessToken.Purpose;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
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
 * The access of the readers (Phase 6) through the real path: security chain, transactions and PostgreSQL, with no
 * test transaction (so a rollback is a rollback and what is committed has to be removed after each test). The link
 * tokens are written straight through the repository port with a known value, since the real ones only exist in the
 * e-mail; the invitation that sends the e-mail is checked by what it leaves in the database.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ReaderAccessIntegrationTest {

    private static final String PASSWORD = "Rd-Acc3ss#Pass";
    private static final String NEW_PASSWORD = "N3w-Rd#Passw0rd";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private ReaderAccessTokenRepository accessTokens;

    private long operatorId;
    private long readerId;

    @BeforeEach
    void createAnOperatorAndAReader() {
        cleanUp();
        operatorId = jdbcTemplate.queryForObject("""
                insert into app_user (login, password_hash, role) values ('racc-operator', ?, 'OPERATOR') returning id
                """, Long.class, passwordHasher.hash(PASSWORD));
        readerId = newReader("RACC-DOC-1", "racc-ana@example.com");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from audit_log");
        jdbcTemplate.update("delete from refresh_token where user_id in (select id from app_user where login like 'racc-%')");
        jdbcTemplate.update("delete from reader_access_token where reader_id in "
                + "(select id from reader where document like 'RACC-DOC-%')");
        jdbcTemplate.update("delete from app_user where login like 'racc-%'");
        jdbcTemplate.update("delete from reader where document like 'RACC-DOC-%'");
    }

    private long newReader(String document, String email) {
        return jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Ana Souza', ?, ?, date '1990-01-01') returning id
                """, Long.class, document, email);
    }

    private String operator() {
        return "Bearer " + TestTokens.signed(encoder, properties.issuer(), String.valueOf(operatorId), "OPERATOR",
                Instant.now(), Instant.now().plusSeconds(900));
    }

    private String link(Purpose purpose, String rawToken, Duration validity) {
        accessTokens.save(ReaderAccessToken.issue(purpose, readerId, ReaderAccessToken.hashOf(rawToken),
                Instant.now(), validity));
        return rawToken;
    }

    private ResultActions accept(String token, String login, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/accept-invitation").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions login(String login, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"));
    }

    private long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    private String accessTokenOf(ResultActions login) throws Exception {
        var body = login.andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    private static String payloadOf(String jwt) {
        return new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
    }

    private long grantAccess(String login) throws Exception {
        accept(link(Purpose.INVITATION, "invite-" + login, Duration.ofHours(72)), login, PASSWORD)
                .andExpect(status().isNoContent());
        return count("select id from app_user where login = ?", login);
    }

    // invitation

    @Test
    void invitingShouldLeaveOnlyTheHashOfTheTokenAndAnAuditEntryWithoutSecrets() throws Exception {
        mockMvc.perform(post("/api/v1/readers/" + readerId + "/access-invitations").header("Authorization", operator()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.readerId").value(readerId))
                .andExpect(jsonPath("$.token").doesNotExist());

        var hashes = jdbcTemplate.queryForList("select token_hash from reader_access_token where reader_id = ? "
                + "and purpose = 'INVITATION'", String.class, readerId);
        assertThat(hashes).hasSize(1);
        assertThat(hashes.getFirst()).matches("^[0-9a-f]{64}$");
        var audit = jdbcTemplate.queryForList("select * from audit_log where action = 'READER_ACCESS_INVITED'");
        assertThat(audit).hasSize(1);
        assertThat(audit.getFirst().get("actor_id")).isEqualTo(operatorId);
        assertThat(audit.getFirst().get("target_type")).isEqualTo("READER");
        assertThat(audit.getFirst().toString()).doesNotContain("racc-ana@example.com").doesNotContain("Ana Souza");
    }

    @Test
    void aRefusedInvitationShouldLeaveNothingBehind() throws Exception {
        mockMvc.perform(post("/api/v1/readers/999999/access-invitations").header("Authorization", operator()))
                .andExpect(status().isNotFound());
        jdbcTemplate.update("update reader set active = false where id = ?", readerId);
        mockMvc.perform(post("/api/v1/readers/" + readerId + "/access-invitations").header("Authorization", operator()))
                .andExpect(status().isUnprocessableContent());

        assertThat(count("select count(*) from reader_access_token")).isZero();
        assertThat(count("select count(*) from audit_log")).isZero();
    }

    @Test
    void theInvitationRouteShouldBeClosedToTheReader() throws Exception {
        grantAccess("racc-ana");
        var readerToken = accessTokenOf(login("racc-ana", PASSWORD).andExpect(status().isOk()));

        mockMvc.perform(post("/api/v1/readers/" + readerId + "/access-invitations")
                .header("Authorization", "Bearer " + readerToken)).andExpect(status().isForbidden());
    }

    // acceptance

    @Test
    void acceptingShouldCreateTheReaderAccessAndLetTheReaderLogIn() throws Exception {
        var token = link(Purpose.INVITATION, "invite-1", Duration.ofHours(72));

        accept(token, "racc-ana", PASSWORD).andExpect(status().isNoContent());

        var user = jdbcTemplate.queryForMap("select * from app_user where login = 'racc-ana'");
        assertThat(user.get("role")).isEqualTo("READER");
        assertThat(user.get("reader_id")).isEqualTo(readerId);
        assertThat(user.get("password_hash").toString()).startsWith("$2").doesNotContain(PASSWORD);
        assertThat(jdbcTemplate.queryForObject("select used_at is not null from reader_access_token", Boolean.class))
                .isTrue();
        var granted = jdbcTemplate.queryForMap("select * from audit_log where action = 'READER_ACCESS_GRANTED'");
        assertThat(granted.get("actor_id")).isEqualTo(user.get("id"));
        assertThat(granted.toString()).doesNotContain(PASSWORD).doesNotContain("invite-1");

        var response = login("racc-ana", PASSWORD).andExpect(status().isOk());
        var payload = payloadOf(accessTokenOf(response));
        assertThat(payload).contains("\"role\":\"READER\"").contains("\"readerId\":" + readerId);
    }

    @Test
    void aRefusedPasswordShouldRollBackAndLeaveTheInvitationUnused() throws Exception {
        var token = link(Purpose.INVITATION, "invite-2", Duration.ofHours(72));

        accept(token, "racc-ana", "123").andExpect(status().isUnprocessableContent());

        assertThat(count("select count(*) from app_user where login = 'racc-ana'")).isZero();
        assertThat(jdbcTemplate.queryForObject("select used_at is null from reader_access_token", Boolean.class))
                .isTrue();
        assertThat(count("select count(*) from audit_log where action = 'READER_ACCESS_GRANTED'")).isZero();
        accept(token, "racc-ana", PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void aTakenLoginShouldRollBackAndLeaveTheInvitationUnused() throws Exception {
        var token = link(Purpose.INVITATION, "invite-3", Duration.ofHours(72));

        accept(token, "racc-operator", PASSWORD).andExpect(status().isUnprocessableContent());

        assertThat(jdbcTemplate.queryForObject("select used_at is null from reader_access_token", Boolean.class))
                .isTrue();
    }

    @Test
    void anInvitationShouldWorkOnlyOnce() throws Exception {
        var token = link(Purpose.INVITATION, "invite-4", Duration.ofHours(72));
        accept(token, "racc-ana", PASSWORD).andExpect(status().isNoContent());

        accept(token, "racc-outra", PASSWORD).andExpect(status().isUnprocessableContent());

        assertThat(count("select count(*) from app_user where role = 'READER'")).isEqualTo(1);
    }

    @Test
    void anExpiredOrUnknownInvitationShouldBeRefusedWithTheSameAnswer() throws Exception {
        var expired = link(Purpose.INVITATION, "invite-5", Duration.ofSeconds(1));
        Thread.sleep(1100);

        var a = accept(expired, "racc-ana", PASSWORD).andExpect(status().isUnprocessableContent())
                .andReturn().getResponse().getContentAsString();
        var b = accept("nao-existe", "racc-ana", PASSWORD).andExpect(status().isUnprocessableContent())
                .andReturn().getResponse().getContentAsString();

        assertThat(a).isEqualTo(b);
    }

    @Test
    void twoSimultaneousAcceptsOfTheSameInvitationShouldCreateOneAccessOnly() throws Exception {
        var token = link(Purpose.INVITATION, "invite-6", Duration.ofHours(72));
        var pool = Executors.newFixedThreadPool(2);
        try {
            var results = pool.invokeAll(java.util.List.<Callable<Integer>>of(
                    () -> accept(token, "racc-um", PASSWORD).andReturn().getResponse().getStatus(),
                    () -> accept(token, "racc-dois", PASSWORD).andReturn().getResponse().getStatus()));
            var statuses = new java.util.ArrayList<Integer>();
            for (var result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(204, 422);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("select count(*) from app_user where role = 'READER'")).isEqualTo(1);
    }

    // a reader cannot use the system's routes

    @Test
    void aReaderShouldGet403OnTheAdministrativeRoutes() throws Exception {
        grantAccess("racc-ana");
        var auth = "Bearer " + accessTokenOf(login("racc-ana", PASSWORD).andExpect(status().isOk()));

        for (var path : new String[] {"/api/v1/readers", "/api/v1/users", "/api/v1/books",
                "/api/v1/loans", "/api/v1/audit-log", "/api/v1/library-configuration"}) {
            mockMvc.perform(get(path).header("Authorization", auth)).andExpect(status().isForbidden());
        }
    }

    @Test
    void aReaderShouldReadItsOwnProfileOnUsersMe() throws Exception {
        var userId = grantAccess("racc-ana");
        var auth = "Bearer " + accessTokenOf(login("racc-ana", PASSWORD).andExpect(status().isOk()));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.login").value("racc-ana"))
                .andExpect(jsonPath("$.role").value("READER"));
    }

    @Test
    void aReaderWhoseAccessWasRevokedShouldGet401OnUsersMeWithTheOldToken() throws Exception {
        grantAccess("racc-ana");
        var auth = "Bearer " + accessTokenOf(login("racc-ana", PASSWORD).andExpect(status().isOk()));
        mockMvc.perform(delete("/api/v1/readers/" + readerId + "/access").header("Authorization", operator()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", auth)).andExpect(status().isUnauthorized());
    }

    // revocation

    @Test
    void revokingShouldStopTheLoginAndTheRenewalAndBeAudited() throws Exception {
        var userId = grantAccess("racc-ana");
        var session = login("racc-ana", PASSWORD).andExpect(status().isOk());
        var refresh = session.andReturn().getResponse().getCookie("refresh_token");
        assertThat(count("select count(*) from refresh_token where user_id = ? and revoked_at is null", userId))
                .isEqualTo(1);

        mockMvc.perform(delete("/api/v1/readers/" + readerId + "/access").header("Authorization", operator()))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("select active from app_user where id = ?", Boolean.class, userId))
                .isFalse();
        assertThat(count("select count(*) from refresh_token where user_id = ? and revoked_at is null", userId))
                .isZero();
        login("racc-ana", PASSWORD).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").header("X-Requested-With", "XMLHttpRequest").cookie(refresh))
                .andExpect(status().isUnauthorized());
        var audit = jdbcTemplate.queryForMap("select * from audit_log where action = 'READER_ACCESS_REVOKED'");
        assertThat(audit.get("actor_id")).isEqualTo(operatorId);
        assertThat(audit.get("target_id")).isEqualTo(userId);
    }

    @Test
    void deactivatingTheReaderShouldTakeAwayTheAccess() throws Exception {
        var userId = grantAccess("racc-ana");

        mockMvc.perform(delete("/api/v1/readers/" + readerId).header("Authorization", operator()))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("select active from app_user where id = ?", Boolean.class, userId))
                .isFalse();
        login("racc-ana", PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void aRevokedReaderShouldBeInvitedAgainWithANewLogin() throws Exception {
        grantAccess("racc-ana");
        mockMvc.perform(delete("/api/v1/readers/" + readerId + "/access").header("Authorization", operator()))
                .andExpect(status().isNoContent());

        accept(link(Purpose.INVITATION, "invite-again", Duration.ofHours(72)), "racc-ana2", PASSWORD)
                .andExpect(status().isNoContent());

        login("racc-ana2", PASSWORD).andExpect(status().isOk());
        login("racc-ana", PASSWORD).andExpect(status().isUnauthorized());
    }

    // password reset

    @Test
    void forgotPasswordShouldAnswerTheSameForARegisteredAndAnUnknownEmail() throws Exception {
        grantAccess("racc-ana");
        var known = mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"racc-ana@example.com\"}")).andExpect(status().isNoContent())
                .andReturn().getResponse();
        var unknown = mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ninguem@example.com\"}")).andExpect(status().isNoContent())
                .andReturn().getResponse();

        assertThat(known.getContentAsString()).isEqualTo(unknown.getContentAsString());
        assertThat(count("select count(*) from reader_access_token where purpose = 'PASSWORD_RESET'")).isEqualTo(1);
    }

    @Test
    void resettingShouldChangeThePasswordRevokeTheSessionsAndWorkOnce() throws Exception {
        var userId = grantAccess("racc-ana");
        login("racc-ana", PASSWORD).andExpect(status().isOk());
        var token = link(Purpose.PASSWORD_RESET, "reset-1", Duration.ofHours(1));

        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(count("select count(*) from refresh_token where user_id = ? and revoked_at is null", userId))
                .isZero();
        login("racc-ana", PASSWORD).andExpect(status().isUnauthorized());
        login("racc-ana", NEW_PASSWORD).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnprocessableContent());
        var audit = jdbcTemplate.queryForMap("select * from audit_log where action = 'READER_PASSWORD_RESET'");
        assertThat(audit.get("actor_id")).isEqualTo(userId);
        assertThat(audit.toString()).doesNotContain(NEW_PASSWORD).doesNotContain("reset-1");
    }

    @Test
    void aWeakNewPasswordShouldRollBackAndKeepTheResetLinkValid() throws Exception {
        grantAccess("racc-ana");
        var token = link(Purpose.PASSWORD_RESET, "reset-2", Duration.ofHours(1));

        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"password\":\"123\"}"))
                .andExpect(status().isUnprocessableContent());

        login("racc-ana", PASSWORD).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("select used_at is null from reader_access_token "
                + "where purpose = 'PASSWORD_RESET'", Boolean.class)).isTrue();
    }

    @Test
    void anInvitationShouldNotWorkAsAResetLink() throws Exception {
        grantAccess("racc-ana");
        var token = link(Purpose.INVITATION, "invite-as-reset", Duration.ofHours(1));

        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isUnprocessableContent());
    }
}
