package com.riverfount.booksmanager.shared.configuration;

import com.riverfount.booksmanager.shared.adapter.out.mail.MailSenderFactory;
import com.riverfount.booksmanager.shared.adapter.out.mail.MailProperties;
import com.riverfount.booksmanager.shared.application.port.out.NotificationSender;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Chooses the {@link NotificationSender}: SMTP when {@code app.mail.enabled} is true, a log line otherwise. */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
class MailConfiguration {

    @Bean
    NotificationSender notificationSender(MailProperties properties) {
        return MailSenderFactory.create(properties);
    }
}
