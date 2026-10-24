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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Verifies the data step of migration V13__convert_isbn10_to_isbn13.sql (RN01): the ISBN-10 already stored becomes
 * the ISBN-13 of the same book, and the migration stops, naming the books, when the same book is stored with both.
 * It migrates a scratch schema up to V12, fills it and then applies V13; it runs outside a test transaction because
 * Flyway uses its own connections, and the scratch schema is dropped at the end, so no other test sees these rows.
 */
@Import(TestcontainersConfiguration.class)
@JdbcTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BookIsbn13MigrationTest {

    private static final String SCHEMA = "isbn13_backfill";

    @Autowired
    private PostgreSQLContainer postgres;

    @Test
    void shouldConvertTheStoredIsbn10AndKeepTheIsbn13() {
        withScratchSchema((dataSource, template) -> {
            insertBook(template, "8533302274", "Dom Casmurro");
            insertBook(template, "080442957X", "Um livro com X");
            insertBook(template, "0000000043", "Dígito de controle zero");
            insertBook(template, "9780000000002", "Já está em ISBN-13");

            migrateToLatest(dataSource);

            var isbns = template.queryForList("select isbn from " + SCHEMA + ".book", String.class);
            assertThat(isbns).containsExactlyInAnyOrder("9788533302273", "9780804429573", "9780000000040",
                    "9780000000002");
        });
    }

    @Test
    void shouldKeepTheBookIdentifiersSoTheCopiesStayWithTheirBooks() {
        withScratchSchema((dataSource, template) -> {
            insertBook(template, "8533302274", "Dom Casmurro");
            template.update("insert into " + SCHEMA + ".copy (book_id, asset_code, status) "
                    + "select id, 'ISBN-1', 'AVAILABLE' from " + SCHEMA + ".book");
            var idBefore = template.queryForObject("select id from " + SCHEMA + ".book", Long.class);

            migrateToLatest(dataSource);

            assertThat(template.queryForObject("select id from " + SCHEMA + ".book", Long.class)).isEqualTo(idBefore);
            assertThat(template.queryForObject("select count(*) from " + SCHEMA + ".copy where book_id = ?",
                    Long.class, idBefore)).isEqualTo(1L);
        });
    }

    @Test
    void shouldStopNamingTheBooksWhenTheSameBookIsStoredWithBothIsbns() {
        withScratchSchema((dataSource, template) -> {
            insertBook(template, "8533302274", "Dom Casmurro (ISBN-10)");
            insertBook(template, "9788533302273", "Dom Casmurro (ISBN-13)");

            assertThatThrownBy(() -> migrateToLatest(dataSource))
                    .isInstanceOf(FlywayException.class)
                    .hasMessageContaining("8533302274")
                    .hasMessageContaining("9788533302273");

            // nothing was changed: the migration is all or nothing
            assertThat(template.queryForList("select isbn from " + SCHEMA + ".book order by id", String.class))
                    .containsExactly("8533302274", "9788533302273");
        });
    }

    private void withScratchSchema(Scenario scenario) {
        // its own connections: Flyway changes the search_path of the ones it uses, and a pooled one would keep it
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
        var template = new JdbcTemplate(dataSource);
        template.execute("drop schema if exists " + SCHEMA + " cascade");
        try {
            Flyway.configure().dataSource(dataSource).schemas(SCHEMA).target("12").load().migrate();
            scenario.run(dataSource, template);
        } finally {
            template.execute("drop schema if exists " + SCHEMA + " cascade");
        }
    }

    private static void migrateToLatest(DriverManagerDataSource dataSource) {
        Flyway.configure().dataSource(dataSource).schemas(SCHEMA).load().migrate();
    }

    private static void insertBook(JdbcTemplate template, String isbn, String title) {
        template.update("insert into " + SCHEMA + ".category (name) values (?) on conflict do nothing", "C");
        template.update("insert into " + SCHEMA + ".book (isbn, title, category_id) "
                + "select ?, ?, id from " + SCHEMA + ".category where name = 'C'", isbn, title);
    }

    @FunctionalInterface
    private interface Scenario {
        void run(DriverManagerDataSource dataSource, JdbcTemplate template);
    }
}
