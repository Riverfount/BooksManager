package com.riverfount.booksmanager.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.PersistenceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies the schema created by migration V17__create_reader_access.sql (Phase 6): the READER profile tied to a
 * reader, one active access per reader, and the token table that keeps only hashes.
 */
@PersistenceTest
class ReaderAccessMigrationTest {

    private static final String BCRYPT_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BzqhZ8H9V5xQe4Fq0sDq6Z0yR3Wm";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long insertReader(String document) {
        return jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date) values ('Ana', ?, 'ana@example.com', date '1990-01-01')
                returning id""", Long.class, document);
    }

    private void insertUser(String login, String role, Long readerId, boolean active) {
        jdbcTemplate.update("insert into app_user (login, password_hash, role, reader_id, active) values (?, ?, ?, ?, ?)",
                login, BCRYPT_HASH, role, readerId, active);
    }

    @Test
    void shouldHaveTheMigrationRegisteredSuccessfully() {
        var success = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where script = 'V17__create_reader_access.sql'",
                Boolean.class);

        assertThat(success).isTrue();
    }

    @Test
    void shouldAcceptTheReaderProfileWithAReader() {
        var readerId = insertReader("MG-1");

        insertUser("ana", "READER", readerId, true);

        assertThat(jdbcTemplate.queryForObject("select count(*) from app_user where reader_id = ?", Long.class,
                readerId)).isEqualTo(1L);
    }

    @Test
    void shouldRefuseTheReaderProfileWithoutAReader() {
        assertThatThrownBy(() -> insertUser("sem-leitor", "READER", null, true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRefuseAReaderOnAUserThatIsNotAReader() {
        var readerId = insertReader("MG-1b");

        assertThatThrownBy(() -> insertUser("operador", "OPERATOR", readerId, true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldStillRestrictTheRoleToTheKnownProfiles() {
        assertThatThrownBy(() -> insertUser("root", "SUPERUSER", null, true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldAllowOneActiveAccessPerReaderButKeepTheRevokedOnes() {
        var readerId = insertReader("MG-2");
        insertUser("ana-1", "READER", readerId, false);
        insertUser("ana-2", "READER", readerId, true);

        assertThatThrownBy(() -> insertUser("ana-3", "READER", readerId, true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldStoreTheTokenOnlyAsItsHash() {
        var columns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_name = 'reader_access_token'",
                String.class);

        assertThat(columns).containsExactlyInAnyOrder("id", "purpose", "reader_id", "token_hash", "created_at",
                "expires_at", "used_at");
    }

    private static final String INSERT_TOKEN = "insert into reader_access_token "
            + "(purpose, reader_id, token_hash, created_at, expires_at) values (?, ?, ?, now(), now() + ?::interval)";

    @Test
    void shouldRestrictThePurpose() {
        var readerId = insertReader("MG-3");

        assertThatThrownBy(() -> jdbcTemplate.update(INSERT_TOKEN, "OTHER", readerId, "b".repeat(64), "1 hour"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRequireTheTokenToExpireAfterItIsCreated() {
        var readerId = insertReader("MG-4");

        assertThatThrownBy(() -> jdbcTemplate.update(INSERT_TOKEN, "INVITATION", readerId, "c".repeat(64), "-1 hour"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRefuseARepeatedTokenHash() {
        var readerId = insertReader("MG-5");
        jdbcTemplate.update(INSERT_TOKEN, "INVITATION", readerId, "a".repeat(64), "1 hour");

        assertThatThrownBy(() -> jdbcTemplate.update(INSERT_TOKEN, "INVITATION", readerId, "a".repeat(64), "1 hour"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
