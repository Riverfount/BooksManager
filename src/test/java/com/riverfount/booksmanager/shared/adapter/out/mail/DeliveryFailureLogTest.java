package com.riverfount.booksmanager.shared.adapter.out.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.riverfount.booksmanager.shared.application.port.out.Notification;
import com.riverfount.booksmanager.shared.application.port.out.NotificationDeliveryException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * A failed delivery must not leak the address of the reader into the log (LGPD): the message of an SMTP exception
 * often repeats the recipient ("Invalid Addresses: ..."), so what is logged and what is thrown carry the kind of
 * failure (the class of each cause) and the subject, never the message of the cause.
 */
class DeliveryFailureLogTest {

    private static final String ADDRESS = "ana.souza@example.com";
    private static final Notification MAIL = new Notification(ADDRESS, "Convite para acessar a biblioteca", "texto");

    /** A sender whose delivery fails the way an SMTP server does: with the address in the message of the cause. */
    private static final class FailingSender extends AfterCommitNotificationSender {
        @Override
        void deliver(Notification notification) {
            throw new IllegalStateException("could not send",
                    new IllegalArgumentException("Invalid Addresses: <" + ADDRESS + ">"));
        }
    }

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(AfterCommitNotificationSender.class);

    @BeforeEach
    void capture() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void release() {
        logger.detachAppender(appender);
    }

    @Test
    void aFailedDeliveryShouldLogTheSubjectAndTheKindOfFailureButNeverTheAddress() {
        new FailingSender().send(MAIL);

        assertThat(appender.list).hasSize(1);
        var event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).contains("Convite para acessar a biblioteca")
                .contains("IllegalStateException").contains("IllegalArgumentException")
                .doesNotContain(ADDRESS).doesNotContain("ana.souza");
        // no exception object either: its message, and the message of its causes, would carry the address
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    void aConfirmedDeliveryThatFailsShouldThrowWithoutTheAddressAnywhereInTheChain() {
        assertThatThrownBy(() -> new FailingSender().sendAndConfirm(MAIL))
                .isInstanceOf(NotificationDeliveryException.class)
                .satisfies(thrown -> {
                    assertThat(thrown.getMessage()).contains("Convite para acessar a biblioteca")
                            .contains("IllegalStateException").doesNotContain(ADDRESS);
                    for (Throwable cause = thrown.getCause(); cause != null; cause = cause.getCause()) {
                        assertThat(String.valueOf(cause.getMessage())).doesNotContain(ADDRESS);
                    }
                    assertThat(new ThrowableProxy(thrown).getMessage()).doesNotContain(ADDRESS);
                });
    }
}
