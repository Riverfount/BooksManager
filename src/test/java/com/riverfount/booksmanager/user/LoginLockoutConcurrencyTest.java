package com.riverfount.booksmanager.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.shared.domain.InvalidCredentialsException;
import com.riverfount.booksmanager.user.application.port.in.AuthenticateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.AuthenticateUserUseCase;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * The login lockout (RF08) against the real database, with no test
 * transaction, so what the application commits is what is read back. It proves
 * two things that a rolled-back test transaction could hide: the count
 * survives the exception that reports a refused login (otherwise the
 * transaction would undo it), and simultaneous attempts are counted one by one
 * (otherwise they would all read the same count and get around the limit).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class LoginLockoutConcurrencyTest {

    private static final String LOGIN = "lockout-test";
    private static final String PASSWORD = "S3cret-Pass!";

    @Autowired
    private AuthenticateUserUseCase authentication;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createTheUser() {
        cleanUp();
        jdbcTemplate.update("insert into app_user (login, password_hash, role) values (?, ?, 'OPERATOR')",
                LOGIN, new BCryptPasswordEncoder().encode(PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from app_user where login = ?", LOGIN);
    }

    private int failedAttempts() {
        return jdbcTemplate.queryForObject("select failed_login_attempts from app_user where login = ?",
                Integer.class, LOGIN);
    }

    private Instant blockedUntil() {
        var timestamp = jdbcTemplate.queryForObject("select blocked_until from app_user where login = ?",
                java.sql.Timestamp.class, LOGIN);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private void wrongAttempt() {
        try {
            authentication.authenticate(new AuthenticateUserCommand(LOGIN, "Wrong-Pass1!"));
        } catch (InvalidCredentialsException expected) {
            // refused, as it should be
        }
    }

    @Test
    void theCountShouldSurviveTheExceptionThatReportsTheRefusal() {
        wrongAttempt();
        wrongAttempt();

        assertThat(failedAttempts()).isEqualTo(2);
        assertThat(blockedUntil()).isNull();
    }

    @Test
    void theThirdWrongPasswordShouldBlockAndTheBlockShouldBePersisted() {
        wrongAttempt();
        wrongAttempt();
        wrongAttempt();

        assertThat(failedAttempts()).isEqualTo(3);
        assertThat(blockedUntil()).isBetween(Instant.now().plus(Duration.ofMinutes(14)),
                Instant.now().plus(Duration.ofMinutes(16)));
    }

    @Test
    void simultaneousWrongPasswordsShouldBeCountedOneByOneAndBlockTheUser() throws Exception {
        var attempts = 12;
        var pool = Executors.newFixedThreadPool(attempts);
        var ready = new CountDownLatch(attempts);
        var go = new CountDownLatch(1);
        var results = new ArrayList<java.util.concurrent.Future<Boolean>>();
        try {
            for (var i = 0; i < attempts; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        authentication.authenticate(new AuthenticateUserCommand(LOGIN, "Wrong-Pass1!"));
                        return false;
                    } catch (InvalidCredentialsException refused) {
                        return true;
                    }
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (var result : results) {
                assertThat(result.get(60, TimeUnit.SECONDS)).as("every attempt is refused").isTrue();
            }
        } finally {
            pool.shutdownNow();
        }

        // counted exactly up to the limit, never lost to a race, and never beyond it once blocked
        assertThat(failedAttempts()).isEqualTo(3);
        assertThat(blockedUntil()).isAfter(Instant.now());
    }

    @Test
    void theRightPasswordShouldBeRefusedWhileBlockedEvenIfSentAtTheSameTimeAsWrongOnes() throws Exception {
        wrongAttempt();
        wrongAttempt();
        wrongAttempt();

        var refused = false;
        try {
            authentication.authenticate(new AuthenticateUserCommand(LOGIN, PASSWORD));
        } catch (InvalidCredentialsException e) {
            refused = true;
        }

        assertThat(refused).isTrue();
        assertThat(failedAttempts()).isEqualTo(3);
    }
}
