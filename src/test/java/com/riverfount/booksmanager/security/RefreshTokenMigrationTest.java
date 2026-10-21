package com.riverfount.booksmanager.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.PersistenceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies the schema created by migration V4__create_refresh_token.sql
 * (RF08, RNF03, RNF07): the refresh token is opaque, so only its hash is
 * stored; the table supports rotation (replaced_by) and revocation
 * (revoked_at), and a user with tokens cannot be deleted (RN14).
 *
 * <p>Each rejection has its own test: in PostgreSQL a failed statement
 * aborts the transaction of the test.
 */
@PersistenceTest
class RefreshTokenMigrationTest {

    private static final String HASH_1 = "a".repeat(64);
    private static final String HASH_2 = "b".repeat(64);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = jdbcTemplate.queryForObject(
                "insert into app_user (login, password_hash, role) values ('maria', 'x', 'OPERATOR') returning id",
                Long.class);
    }

    private Long insertToken(String hash) {
        return jdbcTemplate.queryForObject(
                """
                insert into refresh_token (user_id, token_hash, expires_at, created_at)
                values (?, ?, timestamptz '2026-10-22 12:00:00+00', timestamptz '2026-10-15 12:00:00+00')
                returning id
                """,
                Long.class, userId, hash);
    }

    // shape

    @Test
    void shouldHaveTheMigrationRegisteredSuccessfully() {
        var success = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where script = 'V4__create_refresh_token.sql'",
                Boolean.class);

        assertThat(success).isTrue();
    }

    @Test
    void shouldStoreOnlyTheHashOfTheTokenAndNeverThePlainToken() {
        var columns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_name = 'refresh_token'",
                String.class);

        assertThat(columns).containsExactlyInAnyOrder(
                "id", "user_id", "token_hash", "expires_at", "revoked_at", "replaced_by", "created_at");
        assertThat(columns).noneMatch(column -> column.equals("token") || column.equals("plain_token"));
    }

    @Test
    void shouldRequireTheUserTheHashAndTheDates() {
        for (var column : new String[] {"user_id", "token_hash", "expires_at", "created_at"}) {
            assertThat(isNullable(column)).as("column %s", column).isFalse();
        }
    }

    @Test
    void shouldAcceptNoRevocationAndNoReplacement() {
        for (var column : new String[] {"revoked_at", "replaced_by"}) {
            assertThat(isNullable(column)).as("column %s", column).isTrue();
        }
    }

    @Test
    void shouldHaveNoDefaultForCreatedAtSoTheDateAlwaysComesFromTheApplicationClock() {
        var columnDefault = jdbcTemplate.queryForObject(
                "select column_default from information_schema.columns "
                        + "where table_name = 'refresh_token' and column_name = 'created_at'",
                String.class);

        assertThat(columnDefault).isNull();
    }

    @Test
    void shouldKeepTheDatesWithTheirTimeZone() {
        for (var column : new String[] {"expires_at", "revoked_at", "created_at"}) {
            assertThat(jdbcTemplate.queryForObject(
                    "select data_type from information_schema.columns "
                            + "where table_name = 'refresh_token' and column_name = ?",
                    String.class, column)).as("column %s", column).isEqualTo("timestamp with time zone");
        }
    }

    @Test
    void theHashShouldFitASha256InHexadecimal() {
        assertThat(jdbcTemplate.queryForObject(
                "select character_maximum_length from information_schema.columns "
                        + "where table_name = 'refresh_token' and column_name = 'token_hash'",
                Integer.class)).isGreaterThanOrEqualTo(64);
    }

    // keys and indexes

    @Test
    void shouldReferenceTheUserAndTheReplacingTokenByForeignKey() {
        assertThat(hasForeignKey("user_id", "app_user")).isTrue();
        assertThat(hasForeignKey("replaced_by", "refresh_token")).isTrue();
    }

    @Test
    void shouldHaveAUniqueIndexOnTheHashAndAnIndexOnTheUser() {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes where tablename = 'refresh_token' "
                        + "and indexdef ilike 'create unique index%(token_hash)%'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes where tablename = 'refresh_token' "
                        + "and indexdef ilike '%(user_id)%'",
                Integer.class)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void theHashShouldBeUnique() {
        insertToken(HASH_1);

        assertThatThrownBy(() -> insertToken(HASH_1)).isInstanceOf(DataIntegrityViolationException.class);
    }

    // a user with tokens cannot be deleted (RN14)

    @Test
    void aUserWithTokensCannotBeDeleted() {
        insertToken(HASH_1);

        assertThatThrownBy(() -> jdbcTemplate.update("delete from app_user where id = ?", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aTokenCannotBelongToAnUnknownUser() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                insert into refresh_token (user_id, token_hash, expires_at, created_at)
                values (999999, ?, timestamptz '2026-10-22 12:00:00+00', timestamptz '2026-10-15 12:00:00+00')
                """, HASH_1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // rotation and revocation

    @Test
    void shouldAllowARevokedTokenToPointToItsReplacement() {
        var first = insertToken(HASH_1);
        var second = insertToken(HASH_2);

        jdbcTemplate.update(
                "update refresh_token set revoked_at = timestamptz '2026-10-16 12:00:00+00', replaced_by = ? "
                        + "where id = ?", second, first);

        assertThat(jdbcTemplate.queryForObject("select replaced_by from refresh_token where id = ?", Long.class,
                first)).isEqualTo(second);
    }

    @Test
    void aReplacedTokenMustHaveBeenRevoked() {
        var first = insertToken(HASH_1);
        var second = insertToken(HASH_2);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update refresh_token set replaced_by = ? where id = ?", second, first))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aTokenCannotReplaceItself() {
        var first = insertToken(HASH_1);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update refresh_token set revoked_at = timestamptz '2026-10-16 12:00:00+00', replaced_by = id "
                        + "where id = ?", first))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aTokenMustExpireAfterItIsCreated() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                insert into refresh_token (user_id, token_hash, expires_at, created_at)
                values (?, ?, timestamptz '2026-10-15 12:00:00+00', timestamptz '2026-10-15 12:00:00+00')
                """, userId, HASH_1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private boolean isNullable(String column) {
        return "YES".equals(jdbcTemplate.queryForObject(
                "select is_nullable from information_schema.columns "
                        + "where table_name = 'refresh_token' and column_name = ?",
                String.class, column));
    }

    private boolean hasForeignKey(String column, String referencedTable) {
        var total = jdbcTemplate.queryForObject(
                """
                select count(*) from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu on tc.constraint_name = kcu.constraint_name
                join information_schema.constraint_column_usage ccu on tc.constraint_name = ccu.constraint_name
                where tc.table_name = 'refresh_token' and kcu.column_name = ?
                    and tc.constraint_type = 'FOREIGN KEY' and ccu.table_name = ?
                """,
                Integer.class, column, referencedTable);
        return total != null && total > 0;
    }
}
