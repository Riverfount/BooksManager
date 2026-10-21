package com.riverfount.booksmanager.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.PersistenceTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies the schema created by migration V6__create_library_configuration.sql
 * (RF26, RN16, RNF03): a table with a single row, created with the initial
 * values decided on 10/10/2026, where the policy and its numbers live instead
 * of in the code.
 *
 * <p>Each rejection runs as its own test (a parameterized invocation is one):
 * in PostgreSQL a failed statement aborts the transaction of the test.
 */
@PersistenceTest
class LibraryConfigurationMigrationTest {

    private static final String INSERT = """
            insert into library_configuration (id, penalty_policy, daily_fine_amount, fine_cap,
                suspension_days_per_day, suspension_cap_days, loan_period_days, loan_limit, renewal_limit,
                reservation_pickup_days)
            values (?, 'FINE', 1.00, 10.00, 1, 365, 14, 3, 2, 3)
            """;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // shape

    @Test
    void shouldHaveTheMigrationRegisteredSuccessfully() {
        var success = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where script = 'V6__create_library_configuration.sql'",
                Boolean.class);

        assertThat(success).isTrue();
    }

    @Test
    void shouldHaveTheColumnsOfTheLibraryConfiguration() {
        var columns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_name = 'library_configuration'",
                String.class);

        assertThat(columns).containsExactlyInAnyOrder("id", "penalty_policy", "daily_fine_amount", "fine_cap",
                "suspension_days_per_day", "suspension_cap_days", "loan_period_days", "loan_limit", "renewal_limit",
                "reservation_pickup_days");
    }

    @Test
    void everyColumnShouldBeRequiredSoNoValueFallsBackToACodeConstant() {
        var nullable = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns "
                        + "where table_name = 'library_configuration' and is_nullable = 'YES'",
                String.class);

        assertThat(nullable).isEmpty();
    }

    @Test
    void theMoneyShouldBeAFixedPointNumberNeverAFloat() {
        for (var column : new String[] {"daily_fine_amount", "fine_cap"}) {
            assertThat(jdbcTemplate.queryForObject(
                    "select data_type from information_schema.columns "
                            + "where table_name = 'library_configuration' and column_name = ?",
                    String.class, column)).as("column %s", column).isEqualTo("numeric");
        }
    }

    // the initial values (RN08, decided on 10/10/2026; RN04, RN06, RN07, RN13)

    @Test
    void shouldStartWithExactlyOneRowHoldingTheInitialValues() {
        assertThat(jdbcTemplate.queryForObject("select count(*) from library_configuration", Integer.class))
                .isEqualTo(1);

        var row = jdbcTemplate.queryForMap("select * from library_configuration");

        assertThat(row.get("penalty_policy")).isEqualTo("FINE");
        assertThat((BigDecimal) row.get("daily_fine_amount")).isEqualByComparingTo("1.00");
        assertThat((BigDecimal) row.get("fine_cap")).isEqualByComparingTo("10.00");
        assertThat(row.get("suspension_days_per_day")).isEqualTo(1);
        assertThat(row.get("suspension_cap_days")).isEqualTo(365);
        assertThat(row.get("loan_period_days")).isEqualTo(14);
        assertThat(row.get("loan_limit")).isEqualTo(3);
        assertThat(row.get("renewal_limit")).isEqualTo(2);
        assertThat(row.get("reservation_pickup_days")).isEqualTo(3);
    }

    // a single row

    @Test
    void shouldRefuseASecondRow() {
        assertThatThrownBy(() -> jdbcTemplate.update(INSERT, 2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRefuseTheSameRowTwice() {
        assertThatThrownBy(() -> jdbcTemplate.update(INSERT, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // the policy (RF20, RN08)

    @ParameterizedTest
    @ValueSource(strings = {"FINE", "SUSPENSION", "BOTH", "NONE"})
    void shouldAcceptEachPolicyVariant(String policy) {
        jdbcTemplate.update("update library_configuration set penalty_policy = ?", policy);

        assertThat(jdbcTemplate.queryForObject("select penalty_policy from library_configuration", String.class))
                .isEqualTo(policy);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fine", "MONEY", "", "FINE,NONE"})
    void shouldRefuseAnUnknownPolicy(String policy) {
        assertThatThrownBy(() -> jdbcTemplate.update("update library_configuration set penalty_policy = ?", policy))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // values that make no sense are refused (RN16)

    @ParameterizedTest
    @CsvSource({
            "daily_fine_amount, -0.01",
            "fine_cap, 0",
            "fine_cap, -1",
            "suspension_days_per_day, 0",
            "suspension_days_per_day, -1",
            "suspension_cap_days, 0",
            "suspension_cap_days, -1",
            "loan_period_days, 0",
            "loan_period_days, -1",
            "loan_limit, 0",
            "loan_limit, -1",
            "renewal_limit, -1",
            "reservation_pickup_days, 0",
            "reservation_pickup_days, -1"
    })
    void shouldRefuseAValueThatMakesNoSense(String column, String value) {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update library_configuration set " + column + " = " + value))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "daily_fine_amount, 0",
            "renewal_limit, 0"
    })
    void shouldAcceptZeroWhereItMeansSomething(String column, String value) {
        // no daily fine is how a library that only suspends spends nothing; no renewals is "do not renew"
        jdbcTemplate.update("update library_configuration set " + column + " = " + value);

        assertThat(jdbcTemplate.queryForObject(
                "select " + column + " from library_configuration", BigDecimal.class)).isEqualByComparingTo("0");
    }

    @Test
    void theFineCapShouldNeverBeBelowTheDailyFine() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update library_configuration set daily_fine_amount = 5.00, fine_cap = 4.99"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theFineCapMayEqualTheDailyFine() {
        jdbcTemplate.update("update library_configuration set daily_fine_amount = 5.00, fine_cap = 5.00");

        assertThat(jdbcTemplate.queryForObject("select fine_cap from library_configuration", BigDecimal.class))
                .isEqualByComparingTo("5.00");
    }
}
