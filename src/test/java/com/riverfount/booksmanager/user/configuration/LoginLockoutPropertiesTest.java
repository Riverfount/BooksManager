package com.riverfount.booksmanager.user.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The login lockout settings (RF08): the limit of wrong passwords and how long the block lasts. */
class LoginLockoutPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(UserConfiguration.class);

    @Test
    void shouldBindTheValues() {
        runner.withPropertyValues("app.login-lockout.max-failed-attempts=3", "app.login-lockout.block-duration=15m")
                .run(context -> {
                    var properties = context.getBean(LoginLockoutProperties.class);
                    assertThat(properties.maxFailedAttempts()).isEqualTo(3);
                    assertThat(properties.blockDuration()).isEqualTo(Duration.ofMinutes(15));
                });
    }

    @Test
    void shouldRefuseALimitBelowOne() {
        for (var limit : new String[] {"0", "-1"}) {
            runner.withPropertyValues("app.login-lockout.max-failed-attempts=" + limit,
                            "app.login-lockout.block-duration=15m")
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void shouldRefuseADurationThatIsNotPositive() {
        for (var duration : new String[] {"0s", "-5m"}) {
            runner.withPropertyValues("app.login-lockout.max-failed-attempts=3",
                            "app.login-lockout.block-duration=" + duration)
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void shouldRefuseToStartWithoutTheSettings() {
        runner.run(context -> assertThat(context).hasFailed());
    }
}
