package com.riverfount.booksmanager.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

/**
 * Two administrators demoting each other at the same time (RF09): each one, on
 * its own, is allowed, because the other is still there; if both read the other
 * as still an administrator, both would go through and the system would be left
 * with none. With the administrators locked while the rule is checked, the
 * second request waits, finds the first already done, and is refused. Runs with
 * no test transaction and separate threads, over several rounds.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class UserRoleConcurrencyTest {

    private static final String A = "role-race-a";
    private static final String B = "role-race-b";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    @BeforeEach
    void createTheTwoAdministrators() {
        cleanUp();
        for (var login : List.of(A, B)) {
            jdbcTemplate.update("insert into app_user (login, password_hash, role) values (?, 'x', 'ADMIN')", login);
        }
        // the race is only meaningful if these two are the only active administrators
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from app_user where role = 'ADMIN' and active and login not like 'role-race-%'",
                Integer.class)).as("no other administrator may exist during this test").isZero();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from app_user where login like 'role-race-%'");
    }

    private long idOf(String login) {
        return jdbcTemplate.queryForObject("select id from app_user where login = ?", Long.class, login);
    }

    private int demote(long id) throws Exception {
        var token = TestTokens.signed(encoder, properties.issuer(), "1", "ADMIN", Instant.now(),
                Instant.now().plusSeconds(900));
        return mockMvc.perform(patch("/api/v1/users/" + id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"OPERATOR\"}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void twoAdministratorsDemotingEachOtherNeverLeaveTheSystemWithNone() throws Exception {
        for (var round = 0; round < 6; round++) {
            jdbcTemplate.update("update app_user set role = 'ADMIN' where login like 'role-race-%'");
            var ids = List.of(idOf(A), idOf(B));
            var pool = Executors.newFixedThreadPool(2);
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            var results = new ArrayList<Future<Integer>>();
            try {
                for (var id : ids) {
                    results.add(pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        return demote(id);
                    }));
                }
                assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
                go.countDown();
                var statuses = new ArrayList<Integer>();
                for (var result : results) {
                    statuses.add(result.get(60, TimeUnit.SECONDS));
                }
                // one is allowed, the other finds it is now the last administrator and is refused
                assertThat(statuses).as("round %d", round).containsExactlyInAnyOrder(200, 422);
            } finally {
                pool.shutdownNow();
            }
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from app_user where role = 'ADMIN' and active and login like 'role-race-%'",
                    Integer.class)).as("round %d", round).isEqualTo(1);
        }
    }
}
