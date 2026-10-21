package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.loan.application.port.in.ReserveBookCommand;
import com.riverfount.booksmanager.loan.application.port.in.ReserveBookUseCase;
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
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Reserving a book through the real transaction, with no test transaction (a test transaction would
 * hide the commit, and the index never has a second connection to fight with): two simultaneous
 * requests of the same reader for the same book leave exactly one reservation (RN12, RNF17), and the
 * one that loses is refused like a repeated request.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ReserveBookConcurrencyTest {

    @Autowired
    private ReserveBookUseCase reserveBook;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long bookId;
    private long readerId;

    @BeforeEach
    void createParents() {
        cleanUp();
        var categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Reserve book test') returning id", Long.class);
        bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000309', 'T', ?) returning id",
                Long.class, categoryId);
        jdbcTemplate.update("insert into copy (book_id, asset_code, status) values (?, 'RBC-1', 'BORROWED')",
                bookId);
        readerId = jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', 'RBC-DOC-1', 'r@example.com', date '1990-01-01') returning id
                """, Long.class);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from reservation where book_id in (select id from book where isbn = '9780000000309')");
        jdbcTemplate.update("delete from copy where asset_code = 'RBC-1'");
        jdbcTemplate.update("delete from reader where document = 'RBC-DOC-1'");
        jdbcTemplate.update("delete from book where isbn = '9780000000309'");
        jdbcTemplate.update("delete from category where name = 'Reserve book test'");
    }

    private int reservationsOfTheReader() {
        return jdbcTemplate.queryForObject("select count(*) from reservation where book_id = ? and reader_id = ?",
                Integer.class, bookId, readerId);
    }

    @Test
    void shouldKeepTheReservationAfterTheCommit() {
        var result = reserveBook.reserve(new ReserveBookCommand(bookId, readerId));

        assertThat(result.queuePosition()).isEqualTo(1L);
        assertThat(reservationsOfTheReader()).isEqualTo(1);
    }

    @Test
    void twoSimultaneousReservationsOfTheSameBookByTheSameReaderShouldLeaveOnlyOne() throws Exception {
        var startTogether = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        var results = new ArrayList<Future<Void>>();
        try {
            for (var i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    startTogether.await(10, TimeUnit.SECONDS);
                    reserveBook.reserve(new ReserveBookCommand(bookId, readerId));
                    return null;
                }));
            }
            var passed = 0;
            for (var result : results) {
                try {
                    result.get(20, TimeUnit.SECONDS);
                    passed++;
                } catch (ExecutionException refused) {
                    assertThat(refused.getCause()).isInstanceOf(BusinessRuleException.class)
                            .hasMessageContaining("reserva ativa deste livro");
                }
            }
            assertThat(passed).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(reservationsOfTheReader()).isEqualTo(1);
    }
}
