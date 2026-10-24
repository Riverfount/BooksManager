package com.riverfount.booksmanager.audit.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.loan.application.port.in.PayFineUseCase;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import com.riverfount.booksmanager.user.application.port.out.PasswordHasher;
import java.time.Instant;
import java.util.List;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The audit trail (RF27) through the real path: security chain, transactions and PostgreSQL, with no test
 * transaction (the data is committed, so it is removed after each test). Covers what the issue promises: each
 * listed operation leaves an entry with who, when and what; an operation that is rolled back leaves none; only the
 * administrator reads the trail; and nothing secret is ever in it.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class AuditTrailIntegrationTest {

    private static final String PASSWORD = "Aud1t-Secret#Pass";
    private static final String NEW_PASSWORD = "N3w-Aud1t#Passw0rd";

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
    private PayFineUseCase payFineUseCase;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private long adminId;
    private long operatorId;

    @BeforeEach
    void createAnAdministratorAndAnOperator() {
        cleanUp();
        adminId = newUser("audit-admin", "ADMIN");
        operatorId = newUser("audit-operator", "OPERATOR");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from audit_log");
        jdbcTemplate.update("delete from refresh_token where user_id in (select id from app_user where login like 'audit-%')");
        jdbcTemplate.update("delete from fine where reader_id in (select id from reader where document like 'AU-DOC-%')");
        jdbcTemplate.update("delete from loan where reader_id in (select id from reader where document like 'AU-DOC-%')");
        jdbcTemplate.update("delete from copy where asset_code like 'AU-%'");
        jdbcTemplate.update("delete from reader where document like 'AU-DOC-%'");
        jdbcTemplate.update("delete from book where isbn = '9780000000501'");
        jdbcTemplate.update("delete from category where name = 'Audit test'");
        jdbcTemplate.update("delete from app_user where login like 'audit-%'");
        jdbcTemplate.update("update library_configuration set penalty_policy = 'FINE', loan_period_days = 14");
    }

    private long newUser(String login, String role) {
        return jdbcTemplate.queryForObject("""
                insert into app_user (login, password_hash, role) values (?, ?, ?) returning id
                """, Long.class, login, passwordHasher.hash(PASSWORD), role);
    }

    private String token(long userId, String role) {
        return "Bearer " + TestTokens.signed(encoder, properties.issuer(), String.valueOf(userId), role, Instant.now(),
                Instant.now().plusSeconds(900));
    }

    private String admin() {
        return token(adminId, "ADMIN");
    }

    private ResultActions login(String login, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").header("X-Requested-With", "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"));
    }

    private long countOf(String action) {
        return jdbcTemplate.queryForObject("select count(*) from audit_log where action = ?", Long.class, action);
    }

    private List<java.util.Map<String, Object>> rows(String action) {
        return jdbcTemplate.queryForList("select * from audit_log where action = ? order by id", action);
    }

    private long fine() {
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Audit test') returning id", Long.class);
        var bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000501', 'T', ?) returning id",
                Long.class, categoryId);
        var readerId = jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', 'AU-DOC-1', 'r@example.com', date '1990-01-01') returning id
                """, Long.class);
        var copyId = jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, 'AU-1', 'AVAILABLE') returning id",
                Long.class, bookId);
        var loanId = jdbcTemplate.queryForObject("""
                insert into loan (copy_id, reader_id, pickup_date, due_date, return_date, return_condition)
                values (?, ?, date '2026-01-01', date '2026-01-15', date '2026-01-21', 'GOOD') returning id
                """, Long.class, copyId, readerId);
        return jdbcTemplate.queryForObject(
                "insert into fine (loan_id, reader_id, amount, status) values (?, ?, 6.00, 'PENDING') returning id",
                Long.class, loanId, readerId);
    }

    private long reader() {
        return jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', 'AU-DOC-2', 'r2@example.com', date '1990-01-01') returning id
                """, Long.class);
    }

    // logins: the failures must survive the 401 that reports them

    @Test
    void aSuccessfulLoginShouldBeRecordedAsTheUserWhoLoggedIn() throws Exception {
        login("audit-admin", PASSWORD).andExpect(status().isOk());

        var row = rows("LOGIN_SUCCESS").getFirst();
        assertThat(row.get("actor_id")).isEqualTo(adminId);
        assertThat(row.get("actor_login")).isEqualTo("audit-admin");
        assertThat(row.get("target_type")).isEqualTo("USER");
        assertThat(row.get("target_id")).isEqualTo(adminId);
        assertThat(row.get("occurred_at")).isNotNull();
    }

    @Test
    void aWrongPasswordShouldStayInTheTrailEvenThoughTheRequestAnswered401() throws Exception {
        login("audit-operator", "wrong-Pass#1").andExpect(status().isUnauthorized());

        var row = rows("LOGIN_FAILURE").getFirst();
        assertThat(row.get("actor_id")).isEqualTo(operatorId);
        assertThat(row.get("actor_login")).isEqualTo("audit-operator");
        assertThat(row.get("summary")).isEqualTo("senha incorreta");
    }

    @Test
    void theThirdWrongPasswordShouldAlsoRecordTheLockout() throws Exception {
        for (var i = 0; i < 3; i++) {
            login("audit-operator", "wrong-Pass#1").andExpect(status().isUnauthorized());
        }

        assertThat(countOf("LOGIN_FAILURE")).isEqualTo(3);
        assertThat(countOf("LOGIN_LOCKOUT")).isEqualTo(1);
        assertThat(rows("LOGIN_LOCKOUT").getFirst().get("actor_login")).isEqualTo("audit-operator");
    }

    @Test
    void aLoginThatNamesNoUserShouldBeRecordedWithoutWhatWasTyped() throws Exception {
        login("typed-by-mistake-s3cret", PASSWORD).andExpect(status().isUnauthorized());

        var row = rows("LOGIN_FAILURE").getFirst();
        assertThat(row.get("actor_id")).isNull();
        assertThat(row.get("target_type")).isNull();
        assertThat(row.get("summary")).isEqualTo("login desconhecido");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_log where summary like '%s3cret%' or actor_login like '%s3cret%'",
                Long.class)).isZero();
    }

    // the operations of the administrator

    @Test
    void theUserOperationsShouldEachLeaveAnEntryWithTheAdministratorAsTheActor() throws Exception {
        mockMvc.perform(post("/api/v1/users").header("Authorization", admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"audit-new\",\"password\":\"" + NEW_PASSWORD + "\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isCreated());
        var newId = jdbcTemplate.queryForObject("select id from app_user where login = 'audit-new'", Long.class);

        mockMvc.perform(patch("/api/v1/users/" + newId).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/users/" + newId + "/password").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + NEW_PASSWORD + "x\"}"))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(delete("/api/v1/users/" + newId).header("Authorization", admin()))
                .andExpect(status().isNoContent());

        assertThat(countOf("USER_CREATED")).isEqualTo(1);
        assertThat(countOf("USER_UPDATED")).isEqualTo(1);
        assertThat(countOf("USER_PASSWORD_RESET")).isEqualTo(1);
        assertThat(countOf("USER_DEACTIVATED")).isEqualTo(1);
        var created = rows("USER_CREATED").getFirst();
        assertThat(created.get("actor_id")).isEqualTo(adminId);
        assertThat(created.get("actor_login")).isEqualTo("audit-admin");
        assertThat(created.get("target_id")).isEqualTo(newId);
        assertThat(created.get("summary")).isEqualTo("login: audit-new, perfil: OPERATOR");
        assertThat(rows("USER_UPDATED").getFirst().get("summary")).isEqualTo("perfil: OPERATOR → ADMIN");
    }

    @Test
    void theReaderOperationsShouldEachLeaveAnEntry() throws Exception {
        var id = reader();
        var operator = token(operatorId, "OPERATOR");

        mockMvc.perform(post("/api/v1/readers/" + id + "/block").header("Authorization", operator))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(post("/api/v1/readers/" + id + "/unblock").header("Authorization", operator))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(delete("/api/v1/readers/" + id).header("Authorization", operator))
                .andExpect(status().isNoContent());

        assertThat(countOf("READER_BLOCKED")).isEqualTo(1);
        assertThat(countOf("READER_UNBLOCKED")).isEqualTo(1);
        assertThat(countOf("READER_DEACTIVATED")).isEqualTo(1);
        var blocked = rows("READER_BLOCKED").getFirst();
        assertThat(blocked.get("actor_id")).isEqualTo(operatorId);
        assertThat(blocked.get("actor_login")).isEqualTo("audit-operator");
        assertThat(blocked.get("target_type")).isEqualTo("READER");
        assertThat(blocked.get("target_id")).isEqualTo(id);
    }

    @Test
    void aWaivedFineShouldRecordWhoWaivedItAndWhy() throws Exception {
        var fineId = fine();

        mockMvc.perform(post("/api/v1/fines/" + fineId + "/waive").header("Authorization", token(operatorId, "OPERATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Internação do leitor\"}"))
                .andExpect(status().isOk());

        var row = rows("FINE_WAIVED").getFirst();
        assertThat(row.get("actor_login")).isEqualTo("audit-operator");
        assertThat(row.get("target_type")).isEqualTo("FINE");
        assertThat(row.get("target_id")).isEqualTo(fineId);
        assertThat(row.get("summary")).isEqualTo("abono: Internação do leitor");
    }

    @Test
    void aPaidFineShouldBeRecorded() throws Exception {
        var fineId = fine();

        mockMvc.perform(post("/api/v1/fines/" + fineId + "/pay").header("Authorization", token(operatorId, "OPERATOR")))
                .andExpect(status().isOk());

        assertThat(rows("FINE_PAID").getFirst().get("target_id")).isEqualTo(fineId);
    }

    @Test
    void aChangeToTheLibraryConfigurationShouldRecordTheValuesThatWereSet() throws Exception {
        mockMvc.perform(patch("/api/v1/library-configuration").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"penaltyPolicy\":\"BOTH\",\"loanPeriodDays\":21}"))
                .andExpect(status().isOk());

        var row = rows("LIBRARY_CONFIGURATION_UPDATED").getFirst();
        assertThat(row.get("actor_login")).isEqualTo("audit-admin");
        assertThat(row.get("target_type")).isEqualTo("LIBRARY_CONFIGURATION");
        assertThat(row.get("target_id")).isNull();
        assertThat(row.get("summary")).isEqualTo("penaltyPolicy=BOTH, loanPeriodDays=21");
    }

    // an undone operation leaves no entry

    @Test
    void anOperationThatIsRolledBackShouldLeaveNoEntry() {
        var fineId = fine();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(transaction -> {
            payFineUseCase.pay(fineId);
            throw new IllegalStateException("something fails after the fine was paid");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject("select status from fine where id = ?", String.class, fineId))
                .isEqualTo("PENDING");
        assertThat(countOf("FINE_PAID")).isZero();
    }

    @Test
    void anOperationThatCommitsShouldLeaveItsEntryInTheSameTransaction() {
        var fineId = fine();

        transactionTemplate.executeWithoutResult(transaction -> payFineUseCase.pay(fineId));

        assertThat(jdbcTemplate.queryForObject("select status from fine where id = ?", String.class, fineId))
                .isEqualTo("PAID");
        assertThat(countOf("FINE_PAID")).isEqualTo(1);
    }

    @Test
    void anOperationTheDomainRefusesShouldLeaveNoEntryThroughTheApi() throws Exception {
        var fineId = fine();
        var operator = token(operatorId, "OPERATOR");
        mockMvc.perform(post("/api/v1/fines/" + fineId + "/pay").header("Authorization", operator))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/fines/" + fineId + "/pay").header("Authorization", operator))
                .andExpect(status().isUnprocessableContent());

        assertThat(countOf("FINE_PAID")).isEqualTo(1);
    }

    // reading: only the administrator

    @Test
    void theAdministratorShouldReadTheTrailNewestFirstWithFilters() throws Exception {
        login("audit-operator", "wrong-Pass#1").andExpect(status().isUnauthorized());
        login("audit-admin", PASSWORD).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/audit-log").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].action").value("LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.items[0].actorLogin").value("audit-admin"))
                .andExpect(jsonPath("$.items[1].action").value("LOGIN_FAILURE"))
                .andExpect(jsonPath("$.items[1].actorLogin").value("audit-operator"));

        mockMvc.perform(get("/api/v1/audit-log").param("action", "LOGIN_FAILURE").header("Authorization", admin()))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].actorId").value(operatorId));
        mockMvc.perform(get("/api/v1/audit-log").param("actorId", String.valueOf(adminId)).header("Authorization", admin()))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].action").value("LOGIN_SUCCESS"));
        mockMvc.perform(get("/api/v1/audit-log").param("targetType", "USER").param("targetId", String.valueOf(operatorId))
                        .header("Authorization", admin()))
                .andExpect(jsonPath("$.totalItems").value(1));
        mockMvc.perform(get("/api/v1/audit-log").param("from", "2001-01-01").param("to", "2001-01-31")
                        .header("Authorization", admin()))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void anOperatorShouldNotReadTheTrailAndAnAnonymousCallerNeitherShould() throws Exception {
        login("audit-admin", PASSWORD).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/audit-log").header("Authorization", token(operatorId, "OPERATOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.items").doesNotExist());
        mockMvc.perform(get("/api/v1/audit-log").param("actorId", String.valueOf(adminId))
                        .header("Authorization", token(operatorId, "OPERATOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/audit-log")).andExpect(status().isUnauthorized());
    }

    @Test
    void theTrailShouldHaveNoRouteToAddChangeOrRemoveALine() throws Exception {
        login("audit-admin", PASSWORD).andExpect(status().isOk());
        var id = jdbcTemplate.queryForObject("select id from audit_log limit 1", Long.class);

        for (var request : List.of(
                post("/api/v1/audit-log"), put("/api/v1/audit-log/" + id), patch("/api/v1/audit-log/" + id),
                delete("/api/v1/audit-log/" + id), delete("/api/v1/audit-log"))) {
            mockMvc.perform(request.header("Authorization", admin()).contentType(MediaType.APPLICATION_JSON)
                    .content("{}")).andExpect(status().is4xxClientError());
        }

        assertThat(jdbcTemplate.queryForObject("select count(*) from audit_log", Long.class)).isEqualTo(1);
    }

    // no secret

    @Test
    void nothingSecretShouldEverEndUpInTheTrail() throws Exception {
        var newId = createAndResetAUser();
        login("audit-operator", "wrong-Pass#1").andExpect(status().isUnauthorized());
        login("audit-admin", PASSWORD).andExpect(status().isOk());
        assertThat(newId).isPositive();

        var everything = jdbcTemplate.queryForList("select * from audit_log").toString();

        assertThat(everything).doesNotContain(PASSWORD).doesNotContain(NEW_PASSWORD).doesNotContain("wrong-Pass#1")
                .doesNotContain("$2a$").doesNotContain("$2b$").doesNotContain("eyJ");
        assertThat(jdbcTemplate.queryForObject("select count(*) from audit_log", Long.class)).isGreaterThanOrEqualTo(4);
    }

    private long createAndResetAUser() throws Exception {
        mockMvc.perform(post("/api/v1/users").header("Authorization", admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"audit-new\",\"password\":\"" + NEW_PASSWORD + "\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isCreated());
        var newId = jdbcTemplate.queryForObject("select id from app_user where login = 'audit-new'", Long.class);
        mockMvc.perform(put("/api/v1/users/" + newId + "/password").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + NEW_PASSWORD + "x\"}"))
                .andExpect(status().is2xxSuccessful());
        return newId;
    }
}
