package com.riverfount.booksmanager.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import java.time.Instant;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Creating a user (RF09, HU28) from the HTTP request to the database, with the
 * real chain and PostgreSQL and no test transaction, so that what the
 * application commits is what is read back, and the parallel requests run in
 * separate transactions. The users created here are removed after each test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ExtendWith(OutputCaptureExtension.class)
class UserCreationIntegrationTest {

    private static final String PREFIX = "creation-it-";
    private static final String PASSWORD = "N3w-Pass#word";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from refresh_token where user_id in (select id from app_user where login like ?)",
                PREFIX + "%");
        jdbcTemplate.update("delete from app_user where login like ?", PREFIX + "%");
    }

    private String token(String role) {
        return "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", role, Instant.now(),
                Instant.now().plusSeconds(900));
    }

    private ResultActions create(String bearer, String login, String password, String role) throws Exception {
        var request = post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"password\":\"" + password + "\",\"role\":\"" + role + "\"}");
        if (bearer != null) {
            request.header("Authorization", bearer);
        }
        return mockMvc.perform(request);
    }

    private int usersLike(String login) {
        return jdbcTemplate.queryForObject("select count(*) from app_user where login = ?", Integer.class, login);
    }

    @Test
    void anAdministratorShouldCreateAUserWhoCanThenLogIn() throws Exception {
        var response = create(token("ADMIN"), PREFIX + "maria", PASSWORD, "OPERATOR")
                .andExpect(status().isCreated()).andReturn().getResponse();

        var id = JsonPath.<Integer>read(response.getContentAsString(), "$.id");
        assertThat(response.getHeader("Location")).endsWith("/api/v1/users/" + id);
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + PREFIX + "maria\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldStoreABcryptHashWithTheProfileAndActiveNeverThePlainPassword() throws Exception {
        create(token("ADMIN"), PREFIX + "maria", PASSWORD, "OPERATOR").andExpect(status().isCreated());

        var row = jdbcTemplate.queryForMap(
                "select password_hash, role, active, failed_login_attempts from app_user where login = ?",
                PREFIX + "maria");
        var hash = (String) row.get("password_hash");
        assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD).doesNotContain(PASSWORD);
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD, hash)).isTrue();
        assertThat(row.get("role")).isEqualTo("OPERATOR");
        assertThat(row.get("active")).isEqualTo(true);
        assertThat(row.get("failed_login_attempts")).isEqualTo(0);
    }

    @Test
    void theResponseShouldNeverCarryThePasswordOrItsHash() throws Exception {
        var body = create(token("ADMIN"), PREFIX + "maria", PASSWORD, "OPERATOR").andReturn().getResponse()
                .getContentAsString();
        var hash = jdbcTemplate.queryForObject("select password_hash from app_user where login = ?", String.class,
                PREFIX + "maria");

        assertThat(body).doesNotContain(PASSWORD).doesNotContain(hash).doesNotContain("assword");
    }

    @Test
    void shouldCreateAnotherAdministrator() throws Exception {
        create(token("ADMIN"), PREFIX + "chefe", PASSWORD, "ADMIN").andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject("select role from app_user where login = ?", String.class,
                PREFIX + "chefe")).isEqualTo("ADMIN");
    }

    @Test
    void aRepeatedLoginShouldBeA422EvenWithAnotherCaseAndKeepTheFirstUser() throws Exception {
        create(token("ADMIN"), PREFIX + "maria", PASSWORD, "OPERATOR").andExpect(status().isCreated());

        create(token("ADMIN"), PREFIX + "maria", PASSWORD, "ADMIN").andExpect(status().isUnprocessableContent());
        create(token("ADMIN"), (PREFIX + "maria").toUpperCase(), PASSWORD, "ADMIN")
                .andExpect(status().isUnprocessableContent());

        assertThat(usersLike(PREFIX + "maria")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select role from app_user where login = ?", String.class,
                PREFIX + "maria")).isEqualTo("OPERATOR");
    }

    @Test
    void aWeakPasswordShouldBeA422ThatNamesTheMissingRulesWithoutRepeatingIt() throws Exception {
        var body = create(token("ADMIN"), PREFIX + "maria", "weakpass", "OPERATOR")
                .andExpect(status().isUnprocessableContent()).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("a senha não atende às regras").doesNotContain("weakpass");
        assertThat(usersLike(PREFIX + "maria")).isZero();
    }

    // only an administrator (RNF08)

    @Test
    void anOperatorShouldGet403AndNothingIsCreated() throws Exception {
        var body = create(token("OPERATOR"), PREFIX + "maria", PASSWORD, "OPERATOR")
                .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Você não tem permissão para acessar este recurso.");
        assertThat(usersLike(PREFIX + "maria")).isZero();
    }

    @Test
    void aTokenWithAnUnknownProfileShouldGet403AndNoTokenShouldGet401() throws Exception {
        create(token("SUPERUSER"), PREFIX + "maria", PASSWORD, "OPERATOR").andExpect(status().isForbidden());
        create(null, PREFIX + "maria", PASSWORD, "OPERATOR").andExpect(status().isUnauthorized());

        assertThat(usersLike(PREFIX + "maria")).isZero();
    }

    // simultaneous creations

    @Test
    void twoSimultaneousCreationsOfTheSameLoginShouldLetOnlyOneThrough() throws Exception {
        var attempts = 6;
        var pool = Executors.newFixedThreadPool(attempts);
        var ready = new CountDownLatch(attempts);
        var go = new CountDownLatch(1);
        var results = new ArrayList<Future<Integer>>();
        try {
            for (var i = 0; i < attempts; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return create(token("ADMIN"), PREFIX + "race", PASSWORD, "OPERATOR").andReturn().getResponse()
                            .getStatus();
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            var created = 0;
            for (var result : results) {
                var status = result.get(60, TimeUnit.SECONDS);
                // the database constraint turns the loser of the race into the same 422 as a plain repeat
                assertThat(status).isIn(201, 422);
                if (status == 201) {
                    created++;
                }
            }
            assertThat(created).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(usersLike(PREFIX + "race")).isEqualTo(1);
    }

    // no secret in the log

    @Test
    void thePasswordShouldNeverReachTheLog(CapturedOutput output) throws Exception {
        create(token("ADMIN"), PREFIX + "maria", PASSWORD, "OPERATOR");
        create(token("ADMIN"), PREFIX + "maria", PASSWORD, "OPERATOR");
        create(token("ADMIN"), PREFIX + "weak", "weakpass", "OPERATOR");

        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain("weakpass");
    }
}
