package com.riverfount.booksmanager.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.PersistenceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies the schema created by migration V1__create_catalog.sql
 * (RNF03): the tables and constraints that enforce the catalog's
 * business rules.
 */
@PersistenceTest
class CatalogMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldHaveTheCatalogTables() {
        for (String table : new String[] {"category", "author", "book", "book_author", "copy"}) {
            assertThat(countTable(table)).as("table %s", table).isEqualTo(1);
        }
    }

    @Test
    void shouldHaveTheUnaccentExtensionForTheSearchWithoutAccents() {
        // V15__enable_unaccent.sql (RF05)
        assertThat(jdbcTemplate.queryForObject("select count(*) from pg_extension where extname = 'unaccent'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select unaccent('José García Márquez, canção')", String.class))
                .isEqualTo("Jose Garcia Marquez, cancao");
    }

    @Test
    void shouldHaveTheMigrationRegisteredSuccessfully() {
        var success = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where script = 'V1__create_catalog.sql'",
                Boolean.class);

        assertThat(success).isTrue();
    }

    @Test
    void categoryNameShouldBeUnique() {
        assertThat(hasUniqueConstraint("category", "name")).isTrue();
    }

    @Test
    void bookIsbnShouldBeUniqueAndVarchar13() {
        assertThat(hasUniqueConstraint("book", "isbn")).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                """
                select character_maximum_length from information_schema.columns
                where table_name = 'book' and column_name = 'isbn'
                """,
                Integer.class)).isEqualTo(13);
    }

    @Test
    void bookShouldReferenceCategoryByForeignKey() {
        assertThat(hasForeignKey("book", "category_id", "category")).isTrue();
    }

    @Test
    void bookAuthorShouldHaveCompositePrimaryKeyAndForeignKeys() {
        var keyColumns = jdbcTemplate.queryForList(
                """
                select kcu.column_name from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu on tc.constraint_name = kcu.constraint_name
                where tc.table_name = 'book_author' and tc.constraint_type = 'PRIMARY KEY'
                order by kcu.ordinal_position
                """,
                String.class);

        assertThat(keyColumns).containsExactly("book_id", "author_id");
        assertThat(hasForeignKey("book_author", "book_id", "book")).isTrue();
        assertThat(hasForeignKey("book_author", "author_id", "author")).isTrue();
    }

    @Test
    void copyShouldReferenceBookAndHaveUniqueAssetCode() {
        assertThat(hasForeignKey("copy", "book_id", "book")).isTrue();
        assertThat(hasUniqueConstraint("copy", "asset_code")).isTrue();
    }

    @Test
    void copyShouldHaveNonNullBigintVersionWithZeroDefault() {
        var column = jdbcTemplate.queryForMap(
                """
                select data_type, is_nullable, column_default from information_schema.columns
                where table_name = 'copy' and column_name = 'version'
                """);

        assertThat(column.get("data_type")).isEqualTo("bigint");
        assertThat(column.get("is_nullable")).isEqualTo("NO");
        assertThat((String) column.get("column_default")).startsWith("0");
    }

    @Test
    void copyShouldRejectStatusOutsideTheFiveValidValues() {
        // confirms that @DataJpaTest rolled back any insert from another test
        assertThat(jdbcTemplate.queryForObject("select count(*) from category", Integer.class)).isZero();

        jdbcTemplate.update(
                "insert into category (name, active) values ('Ficção', true)");
        jdbcTemplate.update(
                """
                insert into book (isbn, title, publisher, publication_year, category_id, active)
                values ('9788533302273', 'Dom Casmurro', 'Editora', 1899,
                        (select id from category where name = 'Ficção'), true)
                """);

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                insert into copy (book_id, asset_code, status, active, version)
                values ((select id from book where isbn = '9788533302273'), 'PAT-1', 'STATUS_INVALIDO', true, 0)
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldHaveIndexesOnTheMostQueriedColumnsAndForeignKeys() {
        assertThat(countIndex("book", "title")).isGreaterThanOrEqualTo(1);
        assertThat(countIndex("copy", "book_id")).isGreaterThanOrEqualTo(1);
        assertThat(countIndex("book", "category_id")).isGreaterThanOrEqualTo(1);
        assertThat(countIndex("book_author", "author_id")).isGreaterThanOrEqualTo(1);
    }

    private int countTable(String table) {
        return jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name = ?",
                Integer.class, table);
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

    private boolean hasForeignKey(String table, String column, String referencedTable) {
        var total = jdbcTemplate.queryForObject(
                """
                select count(*) from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu on tc.constraint_name = kcu.constraint_name
                join information_schema.constraint_column_usage ccu on tc.constraint_name = ccu.constraint_name
                where tc.table_name = ? and kcu.column_name = ? and tc.constraint_type = 'FOREIGN KEY'
                    and ccu.table_name = ?
                """,
                Integer.class, table, column, referencedTable);
        return total != null && total > 0;
    }

    private int countIndex(String table, String column) {
        var total = jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes where tablename = ? and indexdef ilike ?",
                Integer.class, table, "%(" + column + ")%");
        return total == null ? 0 : total;
    }
}
