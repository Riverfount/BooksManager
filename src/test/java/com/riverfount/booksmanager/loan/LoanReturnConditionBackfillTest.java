package com.riverfount.booksmanager.loan;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
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
 * Verifies the data step of migration V9__add_return_condition_to_loan.sql (RF15,
 * RNF03): the loans returned before it get GOOD and the active ones stay without
 * condition. It migrates a scratch schema up to V8, fills it and then applies V9;
 * it runs outside a test transaction because Flyway uses its own connections, and
 * the scratch schema is dropped at the end, so no other test sees these rows.
 */
@Import(TestcontainersConfiguration.class)
@JdbcTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LoanReturnConditionBackfillTest {

    private static final String SCHEMA = "loan_backfill";

    @Autowired
    private PostgreSQLContainer postgres;

    @Test
    void shouldGiveGoodToTheLoansReturnedBeforeTheMigration() {
        // its own connections: Flyway changes the search_path of the ones it uses, and a pooled one would keep it
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
        var template = new JdbcTemplate(dataSource);
        template.execute("drop schema if exists " + SCHEMA + " cascade");
        try {
            Flyway.configure().dataSource(dataSource).schemas(SCHEMA).target("8").load().migrate();
            fillWithOneReturnedAndOneActiveLoan(template);

            Flyway.configure().dataSource(dataSource).schemas(SCHEMA).load().migrate();

            var conditions = template.queryForList(
                    "select return_condition from " + SCHEMA + ".loan order by return_date nulls last", String.class);
            assertThat(conditions).containsExactly("GOOD", null);
        } finally {
            template.execute("drop schema if exists " + SCHEMA + " cascade");
        }
    }

    private static void fillWithOneReturnedAndOneActiveLoan(JdbcTemplate template) {
        template.update("insert into " + SCHEMA + ".category (name) values ('C')");
        template.update("insert into " + SCHEMA + ".book (isbn, title, category_id) "
                + "select '9780000000002', 'T', id from " + SCHEMA + ".category");
        template.update("insert into " + SCHEMA + ".copy (book_id, asset_code, status) "
                + "select id, 'BF-' || n, 'AVAILABLE' from " + SCHEMA + ".book, generate_series(1, 2) n");
        template.update("insert into " + SCHEMA + ".reader (name, document, email, birth_date) "
                + "values ('R', 'BF-DOC', 'r@example.com', date '1990-01-01')");
        template.update("insert into " + SCHEMA + ".loan (copy_id, reader_id, pickup_date, due_date, return_date) "
                + "select c.id, r.id, date '2026-01-01', date '2026-01-15', "
                + "case when c.asset_code = 'BF-1' then date '2026-01-10' end "
                + "from " + SCHEMA + ".copy c, " + SCHEMA + ".reader r");
    }
}
