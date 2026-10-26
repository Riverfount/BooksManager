package com.riverfount.booksmanager.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
 * The limits of attempts for the access of the readers (Phase 6, RNF31) through the real path: security chain and
 * PostgreSQL, with no test transaction. Covers what the issue promises: the requests of a reader to their routes are
 * limited per reader (and only the ones the chain accepted count), the "I forgot my password" requests are limited per
 * e-mail without saying anything, and the login of a reader is blocked after wrong passwords like the others.
 *
 * <p>The limiter lives in the memory of the application, so each test uses readers and e-mails of its own.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ReaderAbuseLimitsIntegrationTest {

    private static final String PASSWORD = "Ab-Us3#Passw0rd";
    private static final int LIMIT = 120;

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

    private int sequence;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from audit_log");
        jdbcTemplate.update("delete from reader_access_token where reader_id in "
                + "(select id from reader where document like 'ABU-DOC-%')");
        jdbcTemplate.update("delete from refresh_token where user_id in (select id from app_user where login like 'abu-%')");
        jdbcTemplate.update("delete from app_user where login like 'abu-%'");
        jdbcTemplate.update("delete from reader where document like 'ABU-DOC-%'");
    }

    /** A reader with an access, as the invitation would have left it. */
    private long readerWithAccess(String login, String email) {
        var readerId = jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Ana Souza', ?, ?, date '1990-01-01') returning id
                """, Long.class, "ABU-DOC-" + ++sequence, email);
        jdbcTemplate.update("insert into app_user (login, password_hash, role, reader_id) values (?, ?, 'READER', ?)",
                login, passwordHasher.hash(PASSWORD), readerId);
        return readerId;
    }

    private String reader(long readerId) {
        return "Bearer " + TestTokens.forReader(encoder, properties, 1000 + readerId, readerId);
    }

    private String system(String role) {
        return "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", role, Instant.now(),
                Instant.now().plusSeconds(900));
    }

    private ResultActions books(String authorization) throws Exception {
        var request = get("/api/v1/me/books");
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return mockMvc.perform(request);
    }

    private ResultActions forgot(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions login(String login, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}"));
    }

    private long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    // the requests of a reader

    @Test
    void aReaderShouldBeRefusedWith429AfterTheLimitAndToldHowLongToWait() throws Exception {
        var token = reader(9_000_001L);
        for (var i = 0; i < LIMIT; i++) {
            books(token).andExpect(status().isOk());
        }

        var body = books(token)
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(header().exists("Retry-After"))
                .andReturn();

        var wait = Integer.parseInt(body.getResponse().getHeader("Retry-After"));
        assertThat(wait).isBetween(1, 60);
        assertThat(body.getResponse().getContentAsString()).doesNotContain("9000001").doesNotContain("reader:");
        // still refused, and not served
        books(token).andExpect(status().isTooManyRequests());
    }

    @Test
    void theLimitShouldBeOfTheReaderNotOfEveryone() throws Exception {
        var busy = reader(9_000_002L);
        for (var i = 0; i <= LIMIT; i++) {
            books(busy);
        }
        books(busy).andExpect(status().isTooManyRequests());

        books(reader(9_000_003L)).andExpect(status().isOk());
    }

    @Test
    void theRefusedRequestsOfOthersShouldNotUseUpTheLimitOfTheReader() throws Exception {
        // no token (401) and the system users on the reader's routes (403) are not counted
        for (var i = 0; i < LIMIT + 10; i++) {
            books(null).andExpect(status().isUnauthorized());
            books(system("ADMIN")).andExpect(status().isForbidden());
        }

        var token = reader(9_000_004L);
        for (var i = 0; i < LIMIT; i++) {
            books(token).andExpect(status().isOk());
        }
        books(token).andExpect(status().isTooManyRequests());
    }

    @Test
    void theOtherRoutesShouldNotBeCountedAgainstTheReaderRoutes() throws Exception {
        var token = reader(9_000_005L);
        for (var i = 0; i < LIMIT; i++) {
            mockMvc.perform(get("/api/v1/books").header("Authorization", token)).andExpect(status().isForbidden());
        }

        books(token).andExpect(status().isOk());
    }

    // "I forgot my password"

    @Test
    void shouldSendOnlyThreeResetLinksPerHourPerEmailAndAnswerTheSameAlways() throws Exception {
        var readerId = readerWithAccess("abu-ana", "abu-ana@example.com");

        for (var i = 0; i < 6; i++) {
            forgot("abu-ana@example.com").andExpect(status().isNoContent()).andExpect(content().string(""));
        }

        assertThat(count("select count(*) from reader_access_token where reader_id = ? and purpose = 'PASSWORD_RESET'",
                readerId)).isEqualTo(3);
    }

    @Test
    void theLimitShouldNotTellWhichEmailsExist() throws Exception {
        var known = readerWithAccess("abu-bia", "abu-bia@example.com");
        String[] answers = new String[2];

        for (var i = 0; i < 5; i++) {
            forgot("abu-bia@example.com").andExpect(status().isNoContent());
            forgot("abu-nobody@example.com").andExpect(status().isNoContent());
        }

        // the same answer, both before and after the limit, for an e-mail that exists and for one that does not
        answers[0] = forgot("abu-bia@example.com").andExpect(status().isNoContent()).andReturn().getResponse()
                .getContentAsString();
        answers[1] = forgot("abu-nobody@example.com").andExpect(status().isNoContent()).andReturn().getResponse()
                .getContentAsString();
        assertThat(answers[0]).isEqualTo(answers[1]);
        assertThat(count("select count(*) from reader_access_token where reader_id = ?", known)).isEqualTo(3);
    }

    @Test
    void anotherEmailShouldHaveItsOwnLimit() throws Exception {
        var ana = readerWithAccess("abu-carla", "abu-carla@example.com");
        var bia = readerWithAccess("abu-dani", "abu-dani@example.com");
        for (var i = 0; i < 5; i++) {
            forgot("abu-carla@example.com");
        }

        forgot("abu-dani@example.com").andExpect(status().isNoContent());

        assertThat(count("select count(*) from reader_access_token where reader_id = ?", ana)).isEqualTo(3);
        assertThat(count("select count(*) from reader_access_token where reader_id = ?", bia)).isEqualTo(1);
    }

    // the login of a reader

    @Test
    void theLoginOfAReaderShouldBeBlockedAfterWrongPasswordsLikeEveryoneElses() throws Exception {
        readerWithAccess("abu-eva", "abu-eva@example.com");
        for (var i = 0; i < 3; i++) {
            login("abu-eva", "wrong-Pass#1").andExpect(status().isUnauthorized());
        }

        // even the right password is refused during the block, and the answer does not say the account is blocked
        var refused = login("abu-eva", PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString("bloque")))).andReturn().getResponse()
                .getContentAsString();

        assertThat(count("select count(*) from audit_log where action = 'LOGIN_LOCKOUT'")).isEqualTo(1);
        assertThat(refused).doesNotContain("abu-eva");
    }

    @Test
    void aMinorShouldBeInvitedAtTheirOwnAddressAndNothingOfTheGuardianIsInTheAnswer() throws Exception {
        var minor = jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date, guardian_name, guardian_email)
                values ('Caio Souza', 'ABU-DOC-99', 'abu-caio@example.com', ?, 'Paula Souza', 'abu-paula@example.com')
                returning id
                """, Long.class, java.time.LocalDate.now().minusYears(12));

        mockMvc.perform(post("/api/v1/readers/" + minor + "/access-invitations")
                        .header("Authorization", system("OPERATOR")))
                .andExpect(status().isAccepted())
                .andExpect(content().string(not(containsString("Paula"))))
                .andExpect(content().string(not(containsString("abu-paula"))))
                .andExpect(content().string(not(containsString("abu-caio"))));
    }
}
