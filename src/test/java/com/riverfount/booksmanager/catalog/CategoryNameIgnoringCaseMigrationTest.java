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
 * Verifies migration V20 (category names unique ignoring case): two categories that differ only in case are refused,
 * and the migration stops, naming them, when they already exist. It migrates a scratch schema up to V19, fills it and
 * then applies V20; it runs outside a test transaction because Flyway uses its own connections, and the scratch
 * schema is dropped at the end, so no other test sees these rows.
 */
@Import(TestcontainersConfiguration.class)
@JdbcTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CategoryNameIgnoringCaseMigrationTest {

    private static final String SCHEMA = "category_case_unique";

    @Autowired
    private PostgreSQLContainer postgres;

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

    @FunctionalInterface
    private interface Scenario {
        void run(DriverManagerDataSource dataSource, JdbcTemplate template);
    }
}
