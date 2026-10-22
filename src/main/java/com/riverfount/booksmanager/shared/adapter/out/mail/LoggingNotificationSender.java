package com.riverfount.booksmanager.shared.adapter.out.mail;

import com.riverfount.booksmanager.shared.application.port.out.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used while the mail is off (the default and the tests): nothing is sent, only the subject is logged. */
class LoggingNotificationSender extends AfterCommitNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    void deliver(Notification notification) {
        log.info("Mail is off (app.mail.enabled=false); notification \"{}\" was not sent.", notification.subject());
    }
}
