package com.riverfount.booksmanager.shared.adapter.out.mail;

import com.riverfount.booksmanager.shared.application.port.out.Notification;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Sends the notifications by SMTP with Spring Mail (RF28). Spring Mail does not leave this class. */
class SmtpNotificationSender extends AfterCommitNotificationSender {

    private final JavaMailSenderImpl mailSender;
    private final String from;

    SmtpNotificationSender(MailProperties properties) {
        this.from = properties.from();
        this.mailSender = new JavaMailSenderImpl();
        mailSender.setHost(properties.host());
        mailSender.setPort(properties.port());
        mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        if (properties.hasCredentials()) {
            mailSender.setUsername(properties.username());
            mailSender.setPassword(properties.password());
        }
        Properties javaMail = mailSender.getJavaMailProperties();
        javaMail.put("mail.smtp.auth", String.valueOf(properties.hasCredentials()));
        javaMail.put("mail.smtp.starttls.enable", String.valueOf(properties.startTls()));
        javaMail.put("mail.smtp.connectiontimeout", String.valueOf(properties.connectionTimeout().toMillis()));
        javaMail.put("mail.smtp.timeout", String.valueOf(properties.timeout().toMillis()));
        javaMail.put("mail.smtp.writetimeout", String.valueOf(properties.timeout().toMillis()));
    }

    @Override
    void deliver(Notification notification) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(notification.recipient());
        message.setSubject(notification.subject());
        message.setText(notification.text());
        mailSender.send(message);
    }
}
