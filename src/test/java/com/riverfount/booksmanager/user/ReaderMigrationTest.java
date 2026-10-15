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
 * Verifies the schema created by migration V2__create_reader.sql
 * (RF07, RF10, RNF11): the reader table with its minimal personal data
 * and the constraints that enforce the business rules.
 */
@Import(TestcontainersConfiguration.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class ReaderMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldHaveTheMigrationRegisteredSuccessfully() {
        var success = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where script = 'V2__create_reader.sql'",
                Boolean.class);

        assertThat(success).isTrue();
    }

    @Test
    void shouldHaveTheReaderTableWithOnlyTheMinimalPersonalData() {
        var columns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_name = 'reader'",
                String.class);

        assertThat(columns).containsExactlyInAnyOrder(
                "id", "name", "document", "email", "phone", "birth_date",
                "guardian_name", "guardian_email", "guardian_phone", "active", "blocked");
    }

    @Test
    void documentShouldBeUnique() {
        assertThat(hasUniqueConstraint("reader", "document")).isTrue();
    }

    @Test
    void shouldRequireNameDocumentEmailAndBirthDate() {
        for (String column : new String[] {"name", "document", "email", "birth_date"}) {
            assertThat(isNullable(column)).as("column %s", column).isFalse();
        }
    }

    @Test
    void shouldAcceptNullPhoneAndGuardianData() {
        for (String column : new String[] {"phone", "guardian_name", "guardian_email", "guardian_phone"}) {
            assertThat(isNullable(column)).as("column %s", column).isTrue();
        }
    }

    @Test
    void shouldDefaultToActiveAndNotBlocked() {
        jdbcTemplate.update(
                """
                insert into reader (name, document, email, birth_date)
                values ('Maria', '12345678900', 'maria@example.com', date '1990-05-10')
                """);

        var reader = jdbcTemplate.queryForMap("select active, blocked from reader where document = '12345678900'");

        assertThat(reader.get("active")).isEqualTo(true);
        assertThat(reader.get("blocked")).isEqualTo(false);
    }

    @Test
    void shouldRejectDuplicateDocument() {
        jdbcTemplate.update(
                """
                insert into reader (name, document, email, birth_date)
                values ('Maria', 'DOC-1', 'maria@example.com', date '1990-05-10')
                """);

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                insert into reader (name, document, email, birth_date)
                values ('João', 'DOC-1', 'joao@example.com', date '1991-06-11')
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldHaveAnIndexOnName() {
        var total = jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes where tablename = 'reader' and indexdef ilike '%(name)%'",
                Integer.class);

        assertThat(total).isGreaterThanOrEqualTo(1);
    }

    private boolean isNullable(String column) {
        return "YES".equals(jdbcTemplate.queryForObject(
                "select is_nullable from information_schema.columns where table_name = 'reader' and column_name = ?",
                String.class, column));
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
