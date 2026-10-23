package com.riverfount.booksmanager.shared.adapter.out.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.shared.application.port.out.Notification;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * RF28, RNF19 through the real path: a real transaction on PostgreSQL (no test transaction, which would hide the commit
 * and the rollback) and a real SMTP server of test (GreenMail). The e-mail leaves only after the commit and not at all
 * on rollback, and a mail server that is down, or that does not answer in time, neither undoes nor fails the operation.
 *
 * <p>The mail is off in the shared Spring context of the tests (any change of property would start a context of its
 * own), so the SMTP adapter is built by hand, as the application does when the mail is on, and used inside the
 * context's own transaction manager and database. The data is committed, so it is removed after each test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class NotificationSenderTransactionIntegrationTest {

    private static final String CATEGORY = "Notification transaction test";
    private static final Notification NOTIFICATION =
            new Notification("reader@example.com", "Reserva disponível", "Seu livro está guardado.");

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from category where name = ?", CATEGORY);
    }

    private static MailProperties properties(int port, Duration timeout) {
        return new MailProperties(true, "localhost", port, null, null, "biblioteca@example.com", false,
                Duration.ofSeconds(1), timeout);
    }

    private void saveTheOperation() {
        jdbcTemplate.update("insert into category (name) values (?)", CATEGORY);
    }

    private boolean operationWasSaved() {
        return jdbcTemplate.queryForObject("select count(*) from category where name = ?", Integer.class,
                CATEGORY) == 1;
    }

    @Test
    void shouldSendTheEmailOnlyAfterTheCommit() throws Exception {
        var sender = new SmtpNotificationSender(properties(greenMail.getSmtp().getPort(), Duration.ofSeconds(1)));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            saveTheOperation();
            sender.send(NOTIFICATION);
            assertThat(greenMail.getReceivedMessages()).as("still inside the transaction").isEmpty();
        });

        assertThat(operationWasSaved()).isTrue();
        assertThat(greenMail.getReceivedMessages()).hasSize(1);
        assertThat(greenMail.getReceivedMessages()[0].getSubject()).isEqualTo("Reserva disponível");
    }

    @Test
    void shouldSendNothingWhenTheTransactionRollsBack() {
        var sender = new SmtpNotificationSender(properties(greenMail.getSmtp().getPort(), Duration.ofSeconds(1)));

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            saveTheOperation();
            sender.send(NOTIFICATION);
            throw new IllegalStateException("the operation fails after the notice was asked");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(operationWasSaved()).isFalse();
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void shouldKeepTheOperationWhenTheMailServerIsDown() {
        var unusedPort = ServerSetup.PORT_SMTP + 4000;
        var sender = new SmtpNotificationSender(properties(unusedPort, Duration.ofSeconds(1)));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            saveTheOperation();
            sender.send(NOTIFICATION);
        });

        assertThat(operationWasSaved()).isTrue();
    }

    @Test
    void shouldKeepTheOperationWhenTheMailServerDoesNotAnswerInTime() throws Exception {
        try (var silent = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var sender = new SmtpNotificationSender(properties(silent.getLocalPort(), Duration.ofSeconds(1)));
            var started = System.nanoTime();

            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                saveTheOperation();
                sender.send(NOTIFICATION);
            });

            assertThat(operationWasSaved()).isTrue();
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(10));
        }
    }
}
