package com.riverfount.booksmanager.loan.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.loan.application.port.out.LibraryConfigurationRepository;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Two administrators changing different values of the configuration at the same
 * time (RF26). A change saves the whole row, so without a lock the second one
 * would overwrite what the first changed with a stale read. With the row locked
 * for the change, the second waits, reads the first's result and both changes
 * stay. Runs with no test transaction and separate threads.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class LibraryConfigurationConcurrencyTest {

    @Autowired
    private LibraryConfigurationRepository repository;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void restoreTheInitialValues() {
        jdbcTemplate.update("""
                update library_configuration set penalty_policy = 'FINE', daily_fine_amount = 1.00,
                    fine_cap = 10.00, suspension_days_per_day = 1, suspension_cap_days = 365,
                    loan_period_days = 14, loan_limit = 3, renewal_limit = 2, reservation_pickup_days = 3
                """);
    }

    @Test
    void twoSimultaneousChangesOfDifferentValuesShouldBothSurvive() throws Exception {
        var firstHoldsTheRow = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> transactions.executeWithoutResult(status -> {
                var configuration = repository.getForUpdate();
                firstHoldsTheRow.countDown();
                sleep(700); // long enough for the second to try to read the same row
                configuration.update(null, null, null, null, null, 21, null, null, null, null, null);
                repository.save(configuration);
            }));
            var second = pool.submit(() -> {
                firstHoldsTheRow.await();
                transactions.executeWithoutResult(status -> {
                    var configuration = repository.getForUpdate();
                    configuration.update(null, new BigDecimal("2.00"), null, null, null, null, null, null, null, null, null);
                    repository.save(configuration);
                });
                return null;
            });
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        var result = repository.get();
        assertThat(result.getLoanPeriodDays()).as("the first change").isEqualTo(21);
        assertThat(result.getDailyFineAmount()).as("the second change").isEqualByComparingTo("2.00");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
