package com.riverfount.booksmanager.shared.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The limits of attempts (Phase 6): how many requests of a reader and how many password reset requests. */
class RateLimitPropertiesTest {

    private static final String[] VALID = {
            "app.rate-limit.reader-routes.max-attempts=120", "app.rate-limit.reader-routes.window=1m",
            "app.rate-limit.password-reset.max-attempts=3", "app.rate-limit.password-reset.window=1h"};

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RateLimitConfiguration.class);

    @Test
    void shouldBindTheValuesAsPolicies() {
        runner.withPropertyValues(VALID).run(context -> {
            var properties = context.getBean(RateLimitProperties.class);
            assertThat(properties.readerRoutesPolicy().name()).isEqualTo("reader-routes");
            assertThat(properties.readerRoutesPolicy().maxAttempts()).isEqualTo(120);
            assertThat(properties.readerRoutesPolicy().window()).isEqualTo(Duration.ofMinutes(1));
            assertThat(properties.passwordResetPolicy().name()).isEqualTo("password-reset");
            assertThat(properties.passwordResetPolicy().maxAttempts()).isEqualTo(3);
            assertThat(properties.passwordResetPolicy().window()).isEqualTo(Duration.ofHours(1));
        });
    }

    @Test
    void shouldRefuseALimitBelowOneOrAWindowThatIsNotPositive() {
        for (var wrong : new String[][] {
                {"app.rate-limit.reader-routes.max-attempts=0"}, {"app.rate-limit.reader-routes.max-attempts=-5"},
                {"app.rate-limit.reader-routes.window=0s"}, {"app.rate-limit.password-reset.window=-1h"},
                {"app.rate-limit.password-reset.max-attempts=0"}}) {
            runner.withPropertyValues(VALID).withPropertyValues(wrong)
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void shouldFailAtStartupWhenAValueIsMissingInsteadOfTurningTheProtectionOff() {
        runner.withPropertyValues("app.rate-limit.reader-routes.max-attempts=120")
                .run(context -> assertThat(context).hasFailed());
    }
}
