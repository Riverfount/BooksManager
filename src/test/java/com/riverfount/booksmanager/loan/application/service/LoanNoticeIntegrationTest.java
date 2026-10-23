package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.loan.application.port.in.SendLoanNoticesUseCase;
import com.riverfount.booksmanager.loan.application.port.out.LoanNoticeRepository;
import com.riverfount.booksmanager.loan.application.port.out.LoanRepository;
import com.riverfount.booksmanager.loan.domain.LoanNotice;
import com.riverfount.booksmanager.loan.domain.LoanNoticeKind;
import com.riverfount.booksmanager.shared.application.port.out.Notification;
import com.riverfount.booksmanager.shared.application.port.out.NotificationDeliveryException;
import com.riverfount.booksmanager.shared.application.port.out.NotificationSender;
import com.riverfount.booksmanager.user.application.port.in.ReaderContact;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The loan notices (RF28, RNF19) through the real path: the use case, the transactions and PostgreSQL, with no test
 * transaction (it would hide the commit and the rollback). The data is committed, so it is removed after each test.
 * The mail is off in the tests, so the application's sender only logs and confirms; the failure of the sending is
 * shown by wrapping a {@link LoanNoticeSender} with a failing sender in the same kind of transaction the bean has.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class LoanNoticeIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @Autowired
    private SendLoanNoticesUseCase sendLoanNotices;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private LoanNoticeRepository noticeRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long categoryId;
    private long bookId;
    private int sequence;

    @BeforeEach
    void createParents() {
        cleanUp();
        categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Loan notice test') returning id", Long.class);
        bookId = jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values ('9780000000309', 'Dom Casmurro', ?) returning id",
                Long.class, categoryId);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from loan_notice where loan_id in (select l.id from loan l join reader r "
                + "on r.id = l.reader_id where r.document like 'LN-DOC-%')");
        jdbcTemplate.update("delete from loan where reader_id in (select id from reader where document like 'LN-DOC-%')");
        jdbcTemplate.update("delete from copy where asset_code like 'LN-%'");
        jdbcTemplate.update("delete from reader where document like 'LN-DOC-%'");
        jdbcTemplate.update("delete from book where isbn = '9780000000309'");
        jdbcTemplate.update("delete from category where name = 'Loan notice test'");
    }

    private long newReader() {
        return jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('Reader', ?, 'r@example.com', date '1990-01-01') returning id
                """, Long.class, "LN-DOC-" + ++sequence);
    }

    /** An active loan due {@code daysToDue} days from today (negative when late). */
    private long activeLoan(int daysToDue) {
        var copy = jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, ?, 'BORROWED') returning id", Long.class,
                bookId, "LN-" + ++sequence);
        return jdbcTemplate.queryForObject("""
                insert into loan (copy_id, reader_id, pickup_date, due_date) values (?, ?, ?, ?) returning id
                """, Long.class, copy, newReader(), TODAY.minusDays(14).plusDays(daysToDue), TODAY.plusDays(daysToDue));
    }

    private int noticesOf(long loanId) {
        return jdbcTemplate.queryForObject("select count(*) from loan_notice where loan_id = ?", Integer.class,
                loanId);
    }

    @Test
    void shouldRecordTheNoticeOnceAndSendNothingTheSecondTime() {
        var dueSoon = activeLoan(1);
        var farAway = activeLoan(10);

        sendLoanNotices.sendNotices();

        assertThat(noticesOf(dueSoon)).isEqualTo(1);
        assertThat(noticesOf(farAway)).isZero();
        var rows = jdbcTemplate.queryForList("select kind, band, sent_on from loan_notice where loan_id = ?",
                dueSoon);
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.get("kind")).isEqualTo("DUE_SOON");
            assertThat(row.get("band")).isEqualTo(0);
            assertThat(row.get("sent_on")).hasToString(TODAY.toString());
        });

        assertThat(sendLoanNotices.sendNotices()).isZero();
        assertThat(noticesOf(dueSoon)).isEqualTo(1);
    }

    @Test
    void shouldTellALateLoanOnceInTheFirstBandAndNotATimelyOneOnTheDueDate() {
        var late = activeLoan(-3);
        var dueToday = activeLoan(0);

        sendLoanNotices.sendNotices();

        assertThat(jdbcTemplate.queryForList("select kind, band from loan_notice where loan_id = ?", late))
                .singleElement().satisfies(row -> {
                    assertThat(row.get("kind")).isEqualTo("OVERDUE");
                    assertThat(row.get("band")).isEqualTo(0);
                });
        assertThat(jdbcTemplate.queryForObject("select kind from loan_notice where loan_id = ?", String.class,
                dueToday)).isEqualTo("DUE_SOON");
    }

    @Test
    void aReturnedLoanShouldGetNothing() {
        var loan = activeLoan(-3);
        jdbcTemplate.update("update loan set return_date = ?, return_condition = 'GOOD' where id = ?", TODAY, loan);

        sendLoanNotices.sendNotices();

        assertThat(noticesOf(loan)).isZero();
    }

    /** The sender of the notices is transactional, so a failure in the sending rolls its record back. */
    @Test
    void theSenderShouldBeTransactionalSoThatAFailedSendingLeavesNoRecord() {
        assertThat(LoanNoticeSender.class.isAnnotationPresent(Transactional.class)).isTrue();

        var loan = activeLoan(1);
        var notice = new LoanNotice(LoanNoticeKind.DUE_SOON, TODAY.plusDays(1), 0);
        var reader = new ReaderContact("Reader", "r@example.com");
        var failing = new LoanNoticeSender(loanRepository, noticeRepository, new FailingSender());
        var transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.execute(status -> failing.send(loan, notice, 2, TODAY, reader, null)))
                .isInstanceOf(NotificationDeliveryException.class);
        assertThat(noticesOf(loan)).isZero();

        var working = new LoanNoticeSender(loanRepository, noticeRepository, new RecordingSender());
        Boolean sent = transaction.execute(status -> working.send(loan, notice, 2, TODAY, reader, null));
        assertThat(sent).isTrue();
        assertThat(noticesOf(loan)).isEqualTo(1);
    }

    @Test
    void theSameNoticeCannotBeRecordedTwiceSoTwoOverlappingRunsDoNotBothSend() {
        var loan = activeLoan(1);
        var notice = new LoanNotice(LoanNoticeKind.DUE_SOON, TODAY.plusDays(1), 0);
        var reader = new ReaderContact("Reader", "r@example.com");
        var mail = new RecordingSender();
        var sender = new LoanNoticeSender(loanRepository, noticeRepository, mail);
        var transaction = new TransactionTemplate(transactionManager);
        transaction.execute(status -> sender.send(loan, notice, 2, TODAY, reader, null));

        assertThatThrownBy(() -> transaction.execute(status -> sender.send(loan, notice, 2, TODAY, reader, null)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(mail.sent).hasSize(1);
    }

    private static final class FailingSender implements NotificationSender {

        @Override
        public void send(Notification notification) {
            throw new AssertionError("not used");
        }

        @Override
        public void sendAndConfirm(Notification notification) {
            throw new NotificationDeliveryException("mail server down", new RuntimeException());
        }
    }

    private static final class RecordingSender implements NotificationSender {

        final List<Notification> sent = new ArrayList<>();

        @Override
        public void send(Notification notification) {
            throw new AssertionError("not used");
        }

        @Override
        public void sendAndConfirm(Notification notification) {
            sent.add(notification);
        }
    }
}
