package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.loan.application.port.in.RegisterLoanCommand;
import com.riverfount.booksmanager.loan.application.port.in.RegisterLoanUseCase;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.util.ArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

/**
 * Registering a loan through the real transaction, with no test transaction (a test
 * transaction would hide the rollback, and the lock never has a second connection to
 * fight with): two requests for the same copy at the same time leave exactly one loan
 * (RN03, RNF17), and a failure in the last step undoes the first (RNF16).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class RegisterLoanConcurrencyTest {

    @Autowired
    private RegisterLoanUseCase registerLoan;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long copyId;
    private long readerId;
    private long otherReaderId;

    @BeforeEach
    void createParents() {
        cleanUp();
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Register loan test') returning id", Long.class);
        var bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000033', 'T', ?) returning id",
                Long.class, categoryId);
        copyId = jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, 'RLC-1', 'AVAILABLE') returning id",
                Long.class, bookId);
        readerId = newReader("RLC-DOC-1");
        otherReaderId = newReader("RLC-DOC-2");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from loan where copy_id in (select id from copy where asset_code = 'RLC-1')");
        jdbcTemplate.update("delete from copy where asset_code = 'RLC-1'");
        jdbcTemplate.update("delete from reader where document like 'RLC-DOC-%'");
        jdbcTemplate.update("delete from book where isbn = '9780000000033'");
        jdbcTemplate.update("delete from category where name = 'Register loan test'");
    }

    private long newReader(String document) {
        return jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', ?, 'r@example.com', date '1990-01-01') returning id
                """, Long.class, document);
    }

    private String copyStatus() {
        return jdbcTemplate.queryForObject("select status from copy where id = ?", String.class, copyId);
    }

    private int loansOfTheCopy() {
        return jdbcTemplate.queryForObject("select count(*) from loan where copy_id = ?", Integer.class, copyId);
    }

    @Test
    void shouldBorrowTheCopyAndKeepTheLoanWithTheConfiguredPeriod() {
        var loan = registerLoan.register(new RegisterLoanCommand(copyId, readerId));

        assertThat(copyStatus()).isEqualTo("BORROWED");
        var row = jdbcTemplate.queryForMap(
                "select reader_id, due_date - pickup_date as period, return_date from loan where id = ?", loan.getId());
        assertThat(row.get("reader_id")).isEqualTo(readerId);
        assertThat(row.get("period")).isEqualTo(14);
        assertThat(row.get("return_date")).isNull();
    }

    @Test
    void twoSimultaneousLoansOfTheSameCopyShouldLetOnlyOnePass() throws Exception {
        var startTogether = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        var results = new ArrayList<Future<Void>>();
        try {
            for (var reader : new long[] {readerId, otherReaderId}) {
                results.add(pool.submit(() -> {
                    startTogether.await(10, TimeUnit.SECONDS);
                    registerLoan.register(new RegisterLoanCommand(copyId, reader));
                    return null;
                }));
            }
            var passed = 0;
            for (var result : results) {
                try {
                    result.get(20, TimeUnit.SECONDS);
                    passed++;
                } catch (ExecutionException refused) {
                    assertThat(refused.getCause())
                            .isInstanceOfAny(BusinessRuleException.class, OptimisticLockingFailureException.class);
                }
            }
            assertThat(passed).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(loansOfTheCopy()).isEqualTo(1);
        assertThat(copyStatus()).isEqualTo("BORROWED");
    }

    @Test
    void aFailureInTheLastStepShouldUndoMarkingTheCopyAsBorrowed() {
        // a stray active loan the copy's status does not know about: everything passes until the
        // loan is saved, where the database refuses it, after the copy was already marked
        jdbcTemplate.update("""
                insert into loan (copy_id, reader_id, pickup_date, due_date)
                values (?, ?, current_date, current_date + 14)
                """, copyId, otherReaderId);

        assertThatThrownBy(() -> registerLoan.register(new RegisterLoanCommand(copyId, readerId)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("empréstimo ativo");

        assertThat(copyStatus()).as("the copy goes back to what it was").isEqualTo("AVAILABLE");
        assertThat(loansOfTheCopy()).isEqualTo(1);
    }
}
