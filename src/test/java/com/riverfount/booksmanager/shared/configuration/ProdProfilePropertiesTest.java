package com.riverfount.booksmanager.shared.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The production profile (RNF09, RNF10, RNF12, RNF23), read from the real {@code application.properties} and
 * {@code application-prod.properties} on a light context (no database): the proxy headers are trusted, only the
 * health endpoint is exposed and says nothing beyond its status, the refresh cookie stays Secure, the reservations
 * routine is on (the tests turn it off, and the profile turns it on again), and no secret is written in the file.
 */
class ProdProfilePropertiesTest {

    private final ApplicationContextRunner prod = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.profiles.active=prod");

    @Test
    void shouldTrustTheHeadersOfTheProxy() {
        prod.run(context -> assertThat(context.getEnvironment().getProperty("server.forward-headers-strategy"))
                .isEqualTo("native"));
    }

    @Test
    void shouldExposeOnlyTheHealthEndpointWithNoDetailAndNoComponents() {
        prod.run(context -> {
            var environment = context.getEnvironment();
            assertThat(environment.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
            assertThat(environment.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
            assertThat(environment.getProperty("management.endpoint.health.show-components")).isEqualTo("never");
        });
    }

    @Test
    void shouldKeepTheRefreshCookieSecure() {
        prod.run(context -> assertThat(context.getEnvironment().getProperty("app.auth.refresh-cookie.secure"))
                .isEqualTo("true"));
    }

    @Test
    void shouldTurnTheReservationsRoutineBackOnAfterTheTestsTurnedItOff() {
        // without the profile, the configuration of the tests keeps it off
        new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> assertThat(context.getEnvironment()
                        .getProperty("app.reservations.expiration.enabled")).isEqualTo("false"));
        prod.run(context -> assertThat(context.getEnvironment().getProperty("app.reservations.expiration.enabled"))
                .isEqualTo("true"));
    }

    @Test
    void shouldHideInternalErrorsAndKeepTheLogsPlain() {
        prod.run(context -> {
            var environment = context.getEnvironment();
            assertThat(environment.getProperty("server.error.include-stacktrace")).isEqualTo("never");
            assertThat(environment.getProperty("server.error.include-message")).isEqualTo("never");
            assertThat(environment.getProperty("spring.jpa.show-sql")).isEqualTo("false");
            assertThat(environment.getProperty("spring.output.ansi.enabled")).isEqualTo("never");
        });
    }

    @Test
    void shouldKeepTheSecretsOutOfTheFileAndTheConnectionToTheEnvironment() throws IOException {
        var file = Files.readString(Path.of("src/main/resources/application-prod.properties"));

        var assignments = file.lines().map(String::strip).filter(line -> !line.startsWith("#") && line.contains("="))
                .toList();
        // no property of the file gives a value to a secret, a password or the database address
        assertThat(assignments).noneMatch(line -> Pattern.compile("(secret|password|datasource|jwt)",
                Pattern.CASE_INSENSITIVE).matcher(line).find());
        // and the connection to the database is not given by the profile: it comes from the environment
        prod.run(context -> {
            var environment = context.getEnvironment();
            assertThat(environment.getProperty("spring.datasource.url")).isNull();
            assertThat(environment.getProperty("spring.datasource.password")).isNull();
        });
    }
}
