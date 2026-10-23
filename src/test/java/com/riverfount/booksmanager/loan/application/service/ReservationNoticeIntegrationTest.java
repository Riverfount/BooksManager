package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.loan.application.port.in.CancelReservationCommand;
import com.riverfount.booksmanager.loan.application.port.in.CancelReservationUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ExpireReservationsUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ReturnLoanCommand;
import com.riverfount.booksmanager.loan.application.port.in.ReturnLoanUseCase;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The notice to the reader whose reservation gets a copy (RF28, RN13, RNF19) through the real path: the use cases,
 * the transactions and PostgreSQL, with no test transaction (it would hide the commit and the rollback). The mail is
 * off in the tests, so the application's sender only logs the subject, and only after the commit: that log line is
 * what shows a notice went out. The data is committed, so it is removed after each test.
 */
@ExtendWith(OutputCaptureExtension.class)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ReservationNoticeIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @Autowired
    private ReturnLoanUseCase returnLoan;

    @Autowired
    private CancelReservationUseCase cancelReservation;

    @Autowired
    private ExpireReservationsUseCase expireReservations;

    @Autowired
    private ReservationQueue reservationQueue;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long categoryId;
    private int sequence;

    @BeforeEach
    void createParents() {
        cleanUp();
        categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Reservation notice test') returning id", Long.class);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from reservation where reader_id in (select id from reader where document like 'RN-DOC-%')");
        jdbcTemplate.update("delete from loan where reader_id in (select id from reader where document like 'RN-DOC-%')");
        jdbcTemplate.update("delete from copy where asset_code like 'RN-%'");
        jdbcTemplate.update("delete from reader where document like 'RN-DOC-%'");
        jdbcTemplate.update("delete from book where isbn like '97800000006%'");
        jdbcTemplate.update("delete from category where name = 'Reservation notice test'");
    }

    private long newBook(String title) {
        return jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values (?, ?, ?) returning id", Long.class,
                String.format("97800000006%02d", ++sequence), title, categoryId);
    }

    private long newReader() {
        return jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', ?, 'r@example.com', date '1990-01-01') returning id
                """, Long.class, "RN-DOC-" + ++sequence);
    }

    private long newCopy(long book, String status) {
        return jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, ?, ?) returning id", Long.class, book,
                "RN-" + ++sequence, status);
    }

    private long waiting(long book) {
        return jdbcTemplate.queryForObject("""
                insert into reservation (book_id, reader_id, reservation_date, status) values (?, ?, ?, 'ACTIVE')
                returning id
                """, Long.class, book, newReader(), TODAY.minusDays(1));
    }

    private long holding(long book, long copy, LocalDate deadline) {
        return jdbcTemplate.queryForObject("""
                insert into reservation (book_id, reader_id, reservation_date, status, copy_id, pickup_deadline)
                values (?, ?, ?, 'ACTIVE', ?, ?) returning id
                """, Long.class, book, newReader(), deadline.minusDays(10), copy, deadline);
    }

    private static String subjectOf(String title) {
        return "Livro reservado disponível: \"" + title + "\"";
    }

    @Test
    void theReturnOfACopyWithAQueueShouldTellTheFirstOfTheQueue(CapturedOutput output) {
        var book = newBook("Titulo da devolucao");
        var copy = newCopy(book, "BORROWED");
        var loan = jdbcTemplate.queryForObject("""
                insert into loan (copy_id, reader_id, pickup_date, due_date) values (?, ?, ?, ?) returning id
                """, Long.class, copy, newReader(), TODAY.minusDays(5), TODAY.plusDays(9));
        waiting(book);

        returnLoan.returnLoan(new ReturnLoanCommand(loan));

        assertThat(output).contains(subjectOf("Titulo da devolucao"));
    }

    @Test
    void theReturnOfACopyWithoutQueueShouldTellNobody(CapturedOutput output) {
        var book = newBook("Titulo sem fila");
        var copy = newCopy(book, "BORROWED");
        var loan = jdbcTemplate.queryForObject("""
                insert into loan (copy_id, reader_id, pickup_date, due_date) values (?, ?, ?, ?) returning id
                """, Long.class, copy, newReader(), TODAY.minusDays(5), TODAY.plusDays(9));

        returnLoan.returnLoan(new ReturnLoanCommand(loan));

        assertThat(output).doesNotContain(subjectOf("Titulo sem fila"));
    }

    @Test
    void cancellingAReservationThatHoldsACopyShouldTellTheNextOfTheQueue(CapturedOutput output) {
        var book = newBook("Titulo do cancelamento");
        var holding = holding(book, newCopy(book, "RESERVED"), TODAY.plusDays(2));
        waiting(book);

        cancelReservation.cancel(new CancelReservationCommand(holding));

        assertThat(output).contains(subjectOf("Titulo do cancelamento"));
    }

    @Test
    void theExpirationShouldTellTheNextOfTheQueue(CapturedOutput output) {
        var book = newBook("Titulo da expiracao");
        holding(book, newCopy(book, "RESERVED"), TODAY.minusDays(1));
        waiting(book);

        expireReservations.expireOverdue();

        assertThat(output).contains(subjectOf("Titulo da expiracao"));
    }

    @Test
    void anUndoneOperationShouldNotTellAnyone(CapturedOutput output) {
        var book = newBook("Titulo desfeito");
        var copy = newCopy(book, "AVAILABLE");
        var reservation = waiting(book);
        var transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            reservationQueue.passTheTurn(copy, TODAY, 3);
            throw new IllegalStateException("the caller's operation fails after the turn was passed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(output).doesNotContain(subjectOf("Titulo desfeito"));
        assertThat(jdbcTemplate.queryForObject("select copy_id from reservation where id = ?", Long.class,
                reservation)).isNull();
    }
}
