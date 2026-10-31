package com.riverfount.booksmanager.loan.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.PersistenceTest;
import com.riverfount.booksmanager.loan.domain.LibraryConfiguration;
import com.riverfount.booksmanager.loan.domain.PenaltyPolicy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Tests the library configuration persistence adapter against a real PostgreSQL
 * (Testcontainers): the row the migration creates is read with the initial
 * values (RN08, decided on 10/10/2026), and a saved configuration is read back
 * with every field intact (RF26, RN16).
 */
@PersistenceTest
class LibraryConfigurationPersistenceAdapterTest {

    @Autowired
    private LibraryConfigurationPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldReadTheInitialConfigurationTheMigrationCreated() {
        var configuration = adapter.get();

        assertThat(configuration.getId()).isEqualTo(1L);
        assertThat(configuration.getPenaltyPolicy()).isEqualTo(PenaltyPolicy.FINE);
        assertThat(configuration.getDailyFineAmount()).isEqualByComparingTo("1.00");
        assertThat(configuration.getFineCap()).isEqualByComparingTo("10.00");
        assertThat(configuration.getSuspensionDaysPerDay()).isEqualTo(1);
        assertThat(configuration.getSuspensionCapDays()).isEqualTo(365);
        assertThat(configuration.getLoanPeriodDays()).isEqualTo(14);
        assertThat(configuration.getLoanLimit()).isEqualTo(3);
        assertThat(configuration.getRenewalLimit()).isEqualTo(2);
        assertThat(configuration.getReservationPickupDays()).isEqualTo(3);
    }

    @Test
    void shouldSaveAndReadBackEveryField() {
        var configuration = adapter.get();
        configuration.update(PenaltyPolicy.BOTH, new BigDecimal("2.50"), new BigDecimal("30.00"), 2, 90, 21, 5, 0,
                7, null, null);

        var saved = adapter.save(configuration);
        var found = adapter.get();

        for (LibraryConfiguration c : new LibraryConfiguration[] {saved, found}) {
            assertThat(c.getId()).isEqualTo(1L);
            assertThat(c.getPenaltyPolicy()).isEqualTo(PenaltyPolicy.BOTH);
            assertThat(c.getDailyFineAmount()).isEqualByComparingTo("2.50");
            assertThat(c.getFineCap()).isEqualByComparingTo("30.00");
            assertThat(c.getSuspensionDaysPerDay()).isEqualTo(2);
            assertThat(c.getSuspensionCapDays()).isEqualTo(90);
            assertThat(c.getLoanPeriodDays()).isEqualTo(21);
            assertThat(c.getLoanLimit()).isEqualTo(5);
            assertThat(c.getRenewalLimit()).isZero();
            assertThat(c.getReservationPickupDays()).isEqualTo(7);
        }
    }

    @Test
    void shouldUpdateTheSingleRowNeverAddAnother() {
        var configuration = adapter.get();
        configuration.update(PenaltyPolicy.NONE, null, null, null, null, null, null, null, null, null, null);

        adapter.save(configuration);

        assertThat(jdbcTemplate.queryForObject("select count(*) from library_configuration", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select penalty_policy from library_configuration", String.class))
                .isEqualTo("NONE");
    }

    @Test
    void shouldStoreThePolicyAsTextAndTheMoneyWithTwoDecimalPlaces() {
        var configuration = adapter.get();
        configuration.update(PenaltyPolicy.SUSPENSION, new BigDecimal("0.5"), new BigDecimal("12"), null, null, null,
                null, null, null, null, null);

        adapter.save(configuration);

        var row = jdbcTemplate.queryForMap("select penalty_policy, daily_fine_amount, fine_cap "
                + "from library_configuration");
        assertThat(row.get("penalty_policy")).isEqualTo("SUSPENSION");
        assertThat(row.get("daily_fine_amount").toString()).isEqualTo("0.50");
        assertThat(row.get("fine_cap").toString()).isEqualTo("12.00");
    }

    @Test
    void getForUpdateShouldReadTheSameConfiguration() {
        var configuration = adapter.getForUpdate();

        assertThat(configuration.getPenaltyPolicy()).isEqualTo(PenaltyPolicy.FINE);
        assertThat(configuration.getLoanPeriodDays()).isEqualTo(14);
    }

    @Test
    void shouldFailLoudlyIfTheRowIsMissingSinceTheMigrationAlwaysCreatesIt() {
        jdbcTemplate.update("delete from library_configuration");

        assertThatThrownBy(adapter::get)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("V6");
        assertThatThrownBy(adapter::getForUpdate)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldSaveAndReadBackTheAssetCodePrefix() {
        var configuration = adapter.get();
        assertThat(configuration.getAssetCodePrefix()).isEqualTo("PAT");
        configuration.update(null, null, null, null, null, null, null, null, null, null, "BIB");

        var saved = adapter.save(configuration);

        assertThat(saved.getAssetCodePrefix()).isEqualTo("BIB");
        assertThat(adapter.get().getAssetCodePrefix()).isEqualTo("BIB");
    }
}
