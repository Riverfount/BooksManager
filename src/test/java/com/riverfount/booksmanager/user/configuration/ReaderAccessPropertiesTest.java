package com.riverfount.booksmanager.user.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The reader access settings (Phase 6): how long the links live and where they point. */
class ReaderAccessPropertiesTest {

    private static final String[] VALID = {
            "app.reader-access.invitation-validity=72h",
            "app.reader-access.password-reset-validity=1h",
            "app.reader-access.invitation-url=https://app.example.com/convite?token={token}",
            "app.reader-access.password-reset-url=https://app.example.com/senha?token={token}"};

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(UserConfiguration.class)
            .withPropertyValues("app.login-lockout.max-failed-attempts=3", "app.login-lockout.block-duration=15m");

    @Test
    void shouldBindTheValues() {
        runner.withPropertyValues(VALID).run(context -> {
            var properties = context.getBean(ReaderAccessProperties.class);
            assertThat(properties.invitationValidity()).isEqualTo(Duration.ofHours(72));
            assertThat(properties.passwordResetValidity()).isEqualTo(Duration.ofHours(1));
            assertThat(properties.invitationLink("abc")).isEqualTo("https://app.example.com/convite?token=abc");
            assertThat(properties.passwordResetLink("abc")).isEqualTo("https://app.example.com/senha?token=abc");
        });
    }

    @Test
    void shouldRefuseAValidityThatIsNotPositive() {
        for (var validity : new String[] {"0s", "-1h"}) {
            runner.withPropertyValues(VALID).withPropertyValues("app.reader-access.invitation-validity=" + validity)
                    .run(context -> assertThat(context).hasFailed());
            runner.withPropertyValues(VALID).withPropertyValues("app.reader-access.password-reset-validity=" + validity)
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void shouldRefuseALinkWithoutTheTokenPlaceholder() {
        runner.withPropertyValues(VALID)
                .withPropertyValues("app.reader-access.invitation-url=https://app.example.com/convite")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(VALID)
                .withPropertyValues("app.reader-access.password-reset-url=")
                .run(context -> assertThat(context).hasFailed());
    }
}
