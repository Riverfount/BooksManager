package com.riverfount.booksmanager.security.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * The authentication settings (RF08): the refresh token lifetime and the
 * attributes of the cookie that carries it. A combination that browsers would
 * reject fails at startup instead of silently breaking the login.
 */
class AuthPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(AuthProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    private static final String[] DEFAULTS = {"app.auth.refresh-token-ttl=7d", "app.auth.refresh-cookie.name=refresh_token",
            "app.auth.refresh-cookie.secure=true", "app.auth.refresh-cookie.same-site=Strict",
            "app.auth.refresh-cookie.path=/api/v1/auth"};

    @Test
    void shouldBindTheValues() {
        runner.withPropertyValues(DEFAULTS).run(context -> {
            var properties = context.getBean(AuthProperties.class);
            assertThat(properties.refreshTokenTtl()).isEqualTo(Duration.ofDays(7));
            assertThat(properties.refreshCookie().name()).isEqualTo("refresh_token");
            assertThat(properties.refreshCookie().secure()).isTrue();
            assertThat(properties.refreshCookie().sameSite()).isEqualTo("Strict");
            assertThat(properties.refreshCookie().path()).isEqualTo("/api/v1/auth");
        });
    }

    @Test
    void shouldAcceptEachSameSiteValueRegardlessOfCase() {
        for (var value : new String[] {"Strict", "lax", "STRICT"}) {
            runner.withPropertyValues(DEFAULTS).withPropertyValues("app.auth.refresh-cookie.same-site=" + value)
                    .run(context -> assertThat(context).hasNotFailed());
        }
    }

    @Test
    void shouldRefuseAnUnknownSameSite() {
        runner.withPropertyValues(DEFAULTS).withPropertyValues("app.auth.refresh-cookie.same-site=Sideways")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRefuseSameSiteNoneWithoutSecureBecauseBrowsersDropThatCookie() {
        runner.withPropertyValues(DEFAULTS)
                .withPropertyValues("app.auth.refresh-cookie.same-site=None", "app.auth.refresh-cookie.secure=false")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(DEFAULTS)
                .withPropertyValues("app.auth.refresh-cookie.same-site=None", "app.auth.refresh-cookie.secure=true")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void shouldRefuseALifetimeThatIsNotPositive() {
        for (var ttl : new String[] {"0s", "-1d"}) {
            runner.withPropertyValues(DEFAULTS).withPropertyValues("app.auth.refresh-token-ttl=" + ttl)
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void shouldRefuseAMissingCookieNameOrPath() {
        runner.withPropertyValues(DEFAULTS).withPropertyValues("app.auth.refresh-cookie.name=")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(DEFAULTS).withPropertyValues("app.auth.refresh-cookie.path=")
                .run(context -> assertThat(context).hasFailed());
    }
}
