package com.riverfount.booksmanager.shared.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * CORS allows credentials (the refresh token travels in a cookie), which is
 * only safe with exact origins: a wildcard would let any site use the cookie.
 */
class CorsFilterConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CorsFilterConfiguration.class);

    @Test
    void shouldStartWithExactOriginsOrNoOriginAtAll() {
        runner.withPropertyValues("app.cors.allowed-origins=http://localhost:5173,https://app.example.com")
                .run(context -> assertThat(context).hasNotFailed());
        runner.withPropertyValues("app.cors.allowed-origins=")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void shouldRefuseToStartWithAWildcardOriginBecauseCredentialsAreAllowed() {
        runner.withPropertyValues("app.cors.allowed-origins=*")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "APP_CORS_ALLOWED_ORIGINS não pode conter \"*\": o cookie do refresh token exige "
                                    + "origens exatas");
                });
        runner.withPropertyValues("app.cors.allowed-origins=http://localhost:5173,*")
                .run(context -> assertThat(context).hasFailed());
    }
}
