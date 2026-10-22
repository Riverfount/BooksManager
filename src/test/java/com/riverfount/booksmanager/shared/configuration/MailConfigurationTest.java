package com.riverfount.booksmanager.shared.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.shared.adapter.out.mail.MailProperties;
import com.riverfount.booksmanager.shared.application.port.out.Notification;
import com.riverfount.booksmanager.shared.application.port.out.NotificationSender;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** RF28, RNF10: the mail is off unless configured, and a half configuration does not start the application. */
class MailConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(MailConfiguration.class);

    @Test
    void shouldOnlyLogWhenTheMailIsNotConfigured() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(NotificationSender.class);
            assertThat(context.getBean(NotificationSender.class).getClass().getSimpleName())
                    .isEqualTo("LoggingNotificationSender");
            context.getBean(NotificationSender.class).send(new Notification("a@b.c", "Subject", "Text"));
        });
    }

    @Test
    void shouldUseSmtpWhenEnabledAndConfigured() {
        runner.withPropertyValues("app.mail.enabled=true", "app.mail.host=smtp.example.com",
                        "app.mail.from=biblioteca@example.com")
                .run(context -> assertThat(context.getBean(NotificationSender.class).getClass().getSimpleName())
                        .isEqualTo("SmtpNotificationSender"));
    }

    @Test
    void shouldRefuseToStartWhenEnabledWithoutHost() {
        runner.withPropertyValues("app.mail.enabled=true", "app.mail.from=biblioteca@example.com")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldNotExposeTheCredentialsInToString() {
        var properties = new MailProperties(false, "h", 25, "user",
                "secret-pass", "f@x.y", true, Duration.ofSeconds(1), Duration.ofSeconds(1));
        assertThat(properties.toString()).doesNotContain("secret-pass").doesNotContain("user");
    }
}
