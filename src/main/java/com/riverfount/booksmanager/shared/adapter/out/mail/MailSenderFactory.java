package com.riverfount.booksmanager.shared.adapter.out.mail;

import com.riverfount.booksmanager.shared.application.port.out.NotificationSender;

/** Builds the sender the settings ask for; keeps the two implementations package-private. */
public final class MailSenderFactory {

    private MailSenderFactory() {
    }

    public static NotificationSender create(MailProperties properties) {
        return properties.enabled() ? new SmtpNotificationSender(properties) : new LoggingNotificationSender();
    }
}
