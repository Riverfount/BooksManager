package com.riverfount.booksmanager.loan.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.loan.application.port.out.LoanRepository;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The loan under concurrency (RNF17): two requests lending the same copy at the same
 * moment leave exactly one active loan, the other turned into a business rule by the
 * database index; and two transactions changing the same loan do not overwrite each
 * other. Runs with no test transaction and separate threads.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class LoanConcurrencyTest {

    private static final LocalDate PICKUP = LocalDate.of(2026, 11, 1);

    @Autowired
    private LoanRepository repository;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private LoanTestData data;
    private long copyId;
    private long readerId;
    private long otherReaderId;

    @BeforeEach
    void createParents() {
        cleanUp();
        data = new LoanTestData(jdbcTemplate);
        copyId = data.newCopy();
        readerId = data.newReader();
        otherReaderId = data.newReader();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from loan");
        jdbcTemplate.update("delete from copy where asset_code like 'LP-%'");
        jdbcTemplate.update("delete from reader where document like 'LP-DOC-%'");
        jdbcTemplate.update("delete from book where isbn = '9780000000019'");
        jdbcTemplate.update("delete from category where name = 'Loan persistence test'");
    }

    @Test
    void twoSimultaneousLoansOfTheSameCopyShouldLeaveExactlyOneActive() throws Exception {
        var startTogether = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(lend(startTogether, readerId));
            var second = pool.submit(lend(startTogether, otherReaderId));

            var outcomes = new Throwable[2];
            outcomes[0] = outcomeOf(first);
            outcomes[1] = outcomeOf(second);

            var refusals = java.util.Arrays.stream(outcomes).filter(o -> o != null).toList();
            assertThat(refusals).hasSize(1);
            assertThat(refusals.get(0)).isInstanceOf(BusinessRuleException.class);
        } finally {
            pool.shutdownNow();
        }

        var active = jdbcTemplate.queryForObject(
                "select count(*) from loan where copy_id = ? and return_date is null", Integer.class, copyId);
        assertThat(active).isEqualTo(1);
    }

    @Test
    void twoTransactionsChangingTheSameLoanShouldNotOverwriteEachOther() throws Exception {
        var saved = transactions.execute(status -> repository.save(Loan.create(copyId, readerId, PICKUP, 14)));
        var firstHasLoaded = new CountDownLatch(1);
        var secondHasCommitted = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(1);
        try {
            var first = pool.submit(() -> {
                transactions.executeWithoutResult(status -> {
                    var loan = repository.findById(saved.getId()).orElseThrow();
                    firstHasLoaded.countDown();
                    await(secondHasCommitted);
                    loan.returnCopy(PICKUP.plusDays(9));
                    repository.save(loan);
                });
                return null;
            });

            firstHasLoaded.await();
            transactions.executeWithoutResult(status -> {
                var loan = repository.findById(saved.getId()).orElseThrow();
                loan.renew(PICKUP.plusDays(2), 2, 14);
                repository.save(loan);
            });
            secondHasCommitted.countDown();

            assertThatThrownBy(() -> first.get(20, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(OptimisticLockingFailureException.class);
        } finally {
            pool.shutdownNow();
        }

        var stored = repository.findById(saved.getId()).orElseThrow();
        assertThat(stored.getRenewals()).as("the committed renewal stays").isEqualTo(1);
        assertThat(stored.isActive()).as("the stale return was refused").isTrue();
    }

    private Callable<Void> lend(CyclicBarrier startTogether, long reader) {
        return () -> {
            startTogether.await(10, TimeUnit.SECONDS);
            transactions.executeWithoutResult(
                    status -> repository.save(Loan.create(copyId, reader, PICKUP, 14)));
            return null;
        };
    }

    private static Throwable outcomeOf(java.util.concurrent.Future<Void> future) throws Exception {
        try {
            future.get(20, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
