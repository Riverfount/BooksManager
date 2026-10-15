package com.riverfount.booksmanager.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies the schema created by migration V3__create_app_user.sql
 * (RF08, RF09, RNF07): the table of the people who operate the system, with
 * a unique login, a restricted role and the password only as a hash.
 */
@Import(TestcontainersConfiguration.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class AppUserMigrationTest {

    private static final String BCRYPT_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BzqhZ8H9V5xQe4Fq0sDq6Z0yR3Wm";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldHaveTheMigrationRegisteredSuccessfully() {
        var success = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where script = 'V3__create_app_user.sql'",
                Boolean.class);

        assertThat(success).isTrue();
    }

    @Test
    void shouldStoreThePasswordOnlyAsAHashAndNothingElseAboutIt() {
        var columns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_name = 'app_user'",
                String.class);

        assertThat(columns).containsExactlyInAnyOrder("id", "login", "password_hash", "role", "active",
                "failed_login_attempts", "blocked_until");
        assertThat(columns).noneMatch(column -> column.equals("password") || column.equals("plain_password"));
    }

    @Test
    void shouldRequireLoginPasswordHashAndRole() {
        for (var column : new String[] {"login", "password_hash", "role"}) {
            assertThat(isNullable(column)).as("column %s", column).isFalse();
        }
    }

    @Test
    void loginShouldBeUnique() {
        assertThat(hasUniqueConstraint("app_user", "login")).isTrue();

        insertUser("maria", "ADMIN");

        assertThatThrownBy(() -> insertUser("maria", "OPERATOR"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldAcceptTheAdminAndOperatorRoles() {
        insertUser("admin", "ADMIN");
        insertUser("operator", "OPERATOR");

        var total = jdbcTemplate.queryForObject("select count(*) from app_user", Integer.class);

        assertThat(total).isEqualTo(2);
    }

    // each rejection gets its own test: a failed statement aborts the test's transaction in PostgreSQL

    @Test
    void shouldRejectAnUnknownRole() {
        assertThatThrownBy(() -> insertUser("intruder", "SUPERUSER"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectARoleInTheWrongCase() {
        assertThatThrownBy(() -> insertUser("lowercase", "admin"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectAUserWithoutLogin() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into app_user (login, password_hash, role) values (null, ?, 'ADMIN')", BCRYPT_HASH))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldDefaultToActive() {
        insertUser("maria", "OPERATOR");

        var active = jdbcTemplate.queryForObject("select active from app_user where login = 'maria'",
                Boolean.class);

        assertThat(active).isTrue();
    }

    @Test
    void shouldHoldABcryptHash() {
        insertUser("maria", "OPERATOR");

        var stored = jdbcTemplate.queryForObject("select password_hash from app_user where login = 'maria'",
                String.class);

        assertThat(stored).isEqualTo(BCRYPT_HASH);
        assertThat(maxLength("password_hash")).isGreaterThanOrEqualTo(BCRYPT_HASH.length());
    }

    // login lockout (V5__add_login_lockout_to_app_user.sql, RF08)

    @Test
    void shouldHaveTheLockoutMigrationRegisteredSuccessfully() {
        var success = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where script = 'V5__add_login_lockout_to_app_user.sql'",
                Boolean.class);

        assertThat(success).isTrue();
    }

    @Test
    void shouldStartWithNoFailedAttemptsAndNotBlocked() {
        insertUser("maria", "OPERATOR");

        var row = jdbcTemplate.queryForMap(
                "select failed_login_attempts, blocked_until from app_user where login = 'maria'");

        assertThat(row.get("failed_login_attempts")).isEqualTo(0);
        assertThat(row.get("blocked_until")).isNull();
    }

    @Test
    void theFailedAttemptsShouldBeRequiredAndTheBlockOptional() {
        assertThat(isNullable("failed_login_attempts")).isFalse();
        assertThat(isNullable("blocked_until")).isTrue();
    }

    @Test
    void theBlockShouldKeepItsTimeZone() {
        assertThat(jdbcTemplate.queryForObject(
                "select data_type from information_schema.columns "
                        + "where table_name = 'app_user' and column_name = 'blocked_until'",
                String.class)).isEqualTo("timestamp with time zone");
    }

    @Test
    void shouldStoreTheBlockUntilAnInstant() {
        insertUser("maria", "OPERATOR");

        jdbcTemplate.update("update app_user set failed_login_attempts = 3, "
                + "blocked_until = timestamptz '2026-10-15 12:15:00+00' where login = 'maria'");

        var row = jdbcTemplate.queryForMap(
                "select failed_login_attempts, blocked_until from app_user where login = 'maria'");
        assertThat(row.get("failed_login_attempts")).isEqualTo(3);
        assertThat(((java.sql.Timestamp) row.get("blocked_until")).toInstant())
                .isEqualTo(java.time.Instant.parse("2026-10-15T12:15:00Z"));
    }

    @Test
    void theFailedAttemptsCannotBeNegative() {
        insertUser("maria", "OPERATOR");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update app_user set failed_login_attempts = -1 where login = 'maria'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertUser(String login, String role) {
        jdbcTemplate.update("insert into app_user (login, password_hash, role) values (?, ?, ?)",
                login, BCRYPT_HASH, role);
    }

    private boolean isNullable(String column) {
        return "YES".equals(jdbcTemplate.queryForObject(
                "select is_nullable from information_schema.columns "
                        + "where table_name = 'app_user' and column_name = ?",
                String.class, column));
    }

    private int maxLength(String column) {
        return jdbcTemplate.queryForObject(
                "select character_maximum_length from information_schema.columns "
                        + "where table_name = 'app_user' and column_name = ?",
                Integer.class, column);
    }

    private boolean hasUniqueConstraint(String table, String column) {
        var total = jdbcTemplate.queryForObject(
                """
                select count(*) from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu on tc.constraint_name = kcu.constraint_name
                where tc.table_name = ? and kcu.column_name = ? and tc.constraint_type = 'UNIQUE'
                """,
                Integer.class, table, column);
        return total != null && total > 0;
    }
}
