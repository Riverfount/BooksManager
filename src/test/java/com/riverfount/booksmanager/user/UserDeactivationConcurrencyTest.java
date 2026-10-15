package com.riverfount.booksmanager.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Two administrators deactivating each other at the same time (RF09): each
 * request on its own is allowed, because the other administrator is still
 * there. With the administrators locked while the rule is checked, the second
 * request waits, finds the first already done, and is refused, so one active
 * administrator always remains. Runs with no test transaction and separate
 * threads, over several rounds.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class UserDeactivationConcurrencyTest {

    private static final String A = "deact-race-a";
    private static final String B = "deact-race-b";

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
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from app_user where role = 'ADMIN' and active and login not like 'deact-race-%'",
                Integer.class)).as("no other administrator may exist during this test").isZero();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from refresh_token where user_id in "
                + "(select id from app_user where login like 'deact-race-%')");
        jdbcTemplate.update("delete from app_user where login like 'deact-race-%'");
    }

    private long idOf(String login) {
        return jdbcTemplate.queryForObject("select id from app_user where login = ?", Long.class, login);
    }

    /** The administrator {@code actingId} deactivates {@code targetId}. */
    private int deactivate(long actingId, long targetId) throws Exception {
        var token = TestTokens.signed(encoder, properties.issuer(), String.valueOf(actingId), "ADMIN", Instant.now(),
                Instant.now().plusSeconds(900));
        return mockMvc.perform(delete("/api/v1/users/" + targetId).header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void twoAdministratorsDeactivatingEachOtherNeverLeaveTheSystemWithNone() throws Exception {
        for (var round = 0; round < 6; round++) {
            jdbcTemplate.update("update app_user set active = true where login like 'deact-race-%'");
            var a = idOf(A);
            var b = idOf(B);
            var pool = Executors.newFixedThreadPool(2);
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            var results = new ArrayList<Future<Integer>>();
            try {
                for (var pair : List.of(new long[] {a, b}, new long[] {b, a})) {
                    results.add(pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        return deactivate(pair[0], pair[1]);
                    }));
                }
                assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
                go.countDown();
                var statuses = new ArrayList<Integer>();
                for (var result : results) {
                    statuses.add(result.get(60, TimeUnit.SECONDS));
                }
                // one deactivation goes through; the other finds its target is now the last administrator
                assertThat(statuses).as("round %d", round).containsExactlyInAnyOrder(204, 422);
            } finally {
                pool.shutdownNow();
            }
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from app_user where role = 'ADMIN' and active and login like 'deact-race-%'",
                    Integer.class)).as("round %d", round).isEqualTo(1);
        }
    }
}
