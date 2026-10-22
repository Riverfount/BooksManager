package com.riverfount.booksmanager.shared.adapter.out.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.riverfount.booksmanager.shared.application.port.out.Notification;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** RF28, RNF19: the SMTP adapter sends through a real (test) server and never lets a failure escape. */
class SmtpNotificationSenderTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    private static final Notification NOTIFICATION =
            new Notification("reader@example.com", "Devolução próxima", "Seu empréstimo vence amanhã.");

    private MailProperties properties(int port) {
        return new MailProperties(true, "localhost", port, null, null, "biblioteca@example.com", false,
                Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    @Test
    void shouldSendTheEmailThroughTheConfiguredServer() throws Exception {
        var sender = new SmtpNotificationSender(properties(greenMail.getSmtp().getPort()));

        sender.send(NOTIFICATION);

        var received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getSubject()).isEqualTo("Devolução próxima");
        assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo("reader@example.com");
        assertThat(received[0].getFrom()[0].toString()).isEqualTo("biblioteca@example.com");
        assertThat(received[0].getContent().toString()).contains("Seu empréstimo vence amanhã.");
    }

    @Test
    void shouldNotThrowWhenTheServerIsDown() {
        var unusedPort = ServerSetup.PORT_SMTP + 4000;
        var sender = new SmtpNotificationSender(properties(unusedPort));

        assertThatCode(() -> sender.send(NOTIFICATION)).doesNotThrowAnyException();
    }

    @Test
    void shouldSendOnlyAfterTheTransactionCommits() {
        var sender = new SmtpNotificationSender(properties(greenMail.getSmtp().getPort()));
        TransactionSynchronizationManager.initSynchronization();
        try {
            sender.send(NOTIFICATION);
            assertThat(greenMail.getReceivedMessages()).isEmpty();

            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
            assertThat(greenMail.getReceivedMessages()).hasSize(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldNotSendWhenTheTransactionRollsBack() {
        var sender = new SmtpNotificationSender(properties(greenMail.getSmtp().getPort()));
        TransactionSynchronizationManager.initSynchronization();
        try {
            sender.send(NOTIFICATION);
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(1));
            assertThat(greenMail.getReceivedMessages()).isEmpty();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldRejectAnIncompleteConfigurationWhenEnabled() {
        assertThatThrownBy(() -> new MailProperties(true, "", 25, null, null, "biblioteca@example.com", false,
                Duration.ofSeconds(1), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MailProperties(true, "localhost", 25, null, null, " ", false,
                Duration.ofSeconds(1), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
