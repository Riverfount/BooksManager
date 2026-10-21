package com.riverfount.booksmanager.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.catalog.application.port.in.CheckOutCopyUseCase;
import java.util.ArrayList;
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
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Two simultaneous check outs of the same copy (RN03, RNF17): the {@code @Version} of the
 * copy lets only one pass; the other is refused, either by the business rule (it saw the
 * copy already borrowed) or by the optimistic lock (it read the copy before the other
 * committed). Runs with no test transaction and separate threads.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class CopyLoanConcurrencyTest {

    @Autowired
    private CheckOutCopyUseCase checkOut;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long copyId;

    @BeforeEach
    void createTheCopy() {
        cleanUp();
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Copy loan concurrency') returning id", Long.class);
        var bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000026', 'T', ?) returning id",
                Long.class, categoryId);
        copyId = jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, 'CLC-1', 'AVAILABLE') returning id",
                Long.class, bookId);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from copy where asset_code = 'CLC-1'");
        jdbcTemplate.update("delete from book where isbn = '9780000000026'");
        jdbcTemplate.update("delete from category where name = 'Copy loan concurrency'");
    }

    @Test
    void twoSimultaneousCheckOutsOfTheSameCopyShouldLetOnlyOnePass() throws Exception {
        var startTogether = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        var results = new ArrayList<java.util.concurrent.Future<Void>>();
        try {
            for (var i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    startTogether.await(10, TimeUnit.SECONDS);
                    checkOut.checkOut(copyId);
                    return null;
                }));
            }
            var passed = 0;
            for (var result : results) {
                try {
                    result.get(20, TimeUnit.SECONDS);
                    passed++;
                } catch (ExecutionException refused) {
                    assertThat(refused.getCause()).isInstanceOfAny(
                            com.riverfount.booksmanager.shared.domain.BusinessRuleException.class,
                            org.springframework.dao.OptimisticLockingFailureException.class);
                }
            }
            assertThat(passed).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject("select status from copy where id = ?", String.class, copyId))
                .isEqualTo("BORROWED");
    }
}
