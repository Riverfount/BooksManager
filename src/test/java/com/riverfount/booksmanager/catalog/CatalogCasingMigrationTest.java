package com.riverfount.booksmanager.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Verifies the data steps of V20 (category names unique ignoring case) and V21 (casing restored on the names that
 * were stored entirely in uppercase). It migrates a scratch schema up to V19, fills it and then applies the rest; it
 * runs outside a test transaction because Flyway uses its own connections, and the scratch schema is dropped at the
 * end, so no other test sees these rows.
 */
@Import(TestcontainersConfiguration.class)
@JdbcTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CatalogCasingMigrationTest {

    private static final String SCHEMA = "casing_backfill";

    @Autowired
    private PostgreSQLContainer postgres;

    @Test
    void shouldRestoreTheCasingOfTheNamesStoredEntirelyInUppercase() {
        withScratchSchema((dataSource, template) -> {
            insertCategory(template, "PSICOLOGIA INFANTIL");
            insertAuthor(template, "JEAN PIAGET");
            insertBook(template, "9788533302273", "O NASCIMENTO DA INTELIGÊNCIA NA CRIANÇA");

            migrateToLatest(dataSource);

            assertThat(names(template, "category", "name")).contains("Psicologia Infantil").doesNotContain("PSICOLOGIA INFANTIL");
            assertThat(names(template, "author", "name")).containsExactly("Jean Piaget");
            assertThat(names(template, "book", "title")).containsExactly("O Nascimento da Inteligência na Criança");
        });
    }

    @Test
    void shouldLeaveTheNamesWithMixedCaseExactlyAsTheyAre() {
        withScratchSchema((dataSource, template) -> {
            insertCategory(template, "Ficção");
            insertAuthor(template, "Ronald McDonald");
            insertAuthor(template, "j.r.r. tolkien");
            insertBook(template, "9788533302273", "Rei Arthur II");

            migrateToLatest(dataSource);

            assertThat(names(template, "category", "name")).contains("Ficção");
            assertThat(names(template, "author", "name")).containsExactlyInAnyOrder("Ronald McDonald",
                    "j.r.r. tolkien");
            assertThat(names(template, "book", "title")).containsExactly("Rei Arthur II");
        });
    }

    @Test
    void shouldKeepTheIdentifiersSoTheBooksStayWithTheirCategories() {
        withScratchSchema((dataSource, template) -> {
            insertBook(template, "9788533302273", "DOM CASMURRO");
            var categoryBefore = template.queryForObject("select category_id from " + SCHEMA + ".book", Long.class);

            migrateToLatest(dataSource);

            assertThat(template.queryForObject("select category_id from " + SCHEMA + ".book", Long.class))
                    .isEqualTo(categoryBefore);
        });
    }

    @Test
    void shouldRefuseTwoCategoriesThatOnlyDifferInCaseAfterTheMigration() {
        withScratchSchema((dataSource, template) -> {
            migrateToLatest(dataSource);
            insertCategory(template, "Romance");

            assertThatThrownBy(() -> insertCategory(template, "ROMANCE")).isInstanceOf(DuplicateKeyException.class);
        });
    }

    @Test
    void shouldStopNamingTheCategoriesThatAlreadyDifferOnlyInCase() {
        withScratchSchema((dataSource, template) -> {
            insertCategory(template, "Romance");
            insertCategory(template, "ROMANCE");

            assertThatThrownBy(() -> migrateToLatest(dataSource))
                    .isInstanceOf(FlywayException.class)
                    .hasMessageContaining("'Romance', 'ROMANCE'");
            assertThat(names(template, "category", "name")).containsExactlyInAnyOrder("Romance", "ROMANCE");
        });
    }

    private void withScratchSchema(Scenario scenario) {
        // its own connections: Flyway changes the search_path of the ones it uses, and a pooled one would keep it
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
        var template = new JdbcTemplate(dataSource);
        template.execute("drop schema if exists " + SCHEMA + " cascade");
        try {
            Flyway.configure().dataSource(dataSource).schemas(SCHEMA).target("19").load().migrate();
            scenario.run(dataSource, template);
        } finally {
            template.execute("drop schema if exists " + SCHEMA + " cascade");
        }
    }

    private static void migrateToLatest(DriverManagerDataSource dataSource) {
        Flyway.configure().dataSource(dataSource).schemas(SCHEMA).load().migrate();
    }

    private static java.util.List<String> names(JdbcTemplate template, String table, String column) {
        return template.queryForList("select " + column + " from " + SCHEMA + "." + table, String.class);
    }

    private static void insertCategory(JdbcTemplate template, String name) {
        template.update("insert into " + SCHEMA + ".category (name) values (?)", name);
    }

    private static void insertAuthor(JdbcTemplate template, String name) {
        template.update("insert into " + SCHEMA + ".author (name) values (?)", name);
    }

    private static void insertBook(JdbcTemplate template, String isbn, String title) {
        template.update("insert into " + SCHEMA + ".category (name) values ('Categoria') on conflict do nothing");
        template.update("insert into " + SCHEMA + ".book (isbn, title, category_id) "
                + "select ?, ?, id from " + SCHEMA + ".category where name = 'Categoria'", isbn, title);
    }

    @FunctionalInterface
    private interface Scenario {
        void run(DriverManagerDataSource dataSource, JdbcTemplate template);
    }
}
