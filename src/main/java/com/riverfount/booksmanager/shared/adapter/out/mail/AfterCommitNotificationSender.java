package com.riverfount.booksmanager.shared.adapter.out.mail;

import com.riverfount.booksmanager.shared.application.port.out.Notification;
import com.riverfount.booksmanager.shared.application.port.out.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Common behavior of the senders (RNF19): inside a transaction the delivery waits for the commit (and is dropped on
 * rollback, so nobody is told about something that did not happen); outside one it is immediate. Whatever goes
 * wrong in the delivery is logged and swallowed, never thrown to the use case.
 */
abstract class AfterCommitNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(AfterCommitNotificationSender.class);

    @Override
    public final void send(Notification notification) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliverSafely(notification);
                }
            });
        } else {
            deliverSafely(notification);
        }
    }

    private void deliverSafely(Notification notification) {
        try {
            deliver(notification);
        } catch (RuntimeException e) {
            // The recipient address is personal data (LGPD): only the subject and the cause go to the log.
            log.error("Could not deliver the notification \"{}\"; the operation that caused it is not affected.",
                    notification.subject(), e);
        }
    }

    abstract void deliver(Notification notification);
}
