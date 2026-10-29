package com.riverfount.booksmanager.catalog.adapter.out.isbn;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.catalog.application.port.out.BookLookup;
import com.riverfount.booksmanager.catalog.domain.Isbn;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** RF06, RNF10, RNF19: the lookup is off unless configured, and a nonsense setting does not start the application. */
class BookLookupConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(BookLookupConfiguration.class).withBean(Clock.class, Clock::systemUTC);

    @Test
    void shouldKnowNothingWhenTheLookupIsNotConfigured() {
        runner.run(context -> {
            assertThat(context.getBean(BookLookup.class)).isInstanceOf(NoBookLookup.class);
            assertThat(context.getBean(BookLookup.class).findByIsbn(new Isbn("9780000000309"))).isEmpty();
        });
    }

    @Test
    void shouldUseBrasilApiWhenEnabled() {
        runner.withPropertyValues("app.isbn-lookup.enabled=true")
                .run(context -> assertThat(context.getBean(BookLookup.class))
                        .isInstanceOf(BrasilApiBookLookup.class));
    }

    @Test
    void shouldReadTheDefaultsOfTheSettings() {
        runner.run(context -> {
            var properties = context.getBean(IsbnLookupProperties.class);
            assertThat(properties.enabled()).isFalse();
            assertThat(properties.baseUrl()).isEqualTo("https://brasilapi.com.br");
            assertThat(properties.connectionTimeout()).hasSeconds(2);
            assertThat(properties.timeout()).hasSeconds(3);
            assertThat(properties.cacheTtl()).hasMinutes(10);
            assertThat(properties.cacheMaxSize()).isEqualTo(500);
        });
    }

    @Test
    void shouldRefuseToStartWithABlankBaseUrlOrAnEmptyCache() {
        runner.withPropertyValues("app.isbn-lookup.base-url= ").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.isbn-lookup.cache-max-size=0").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void theTestProfileShouldHaveItOffByDefault() {
        // the runner does not load the tests' config; the shared context does, so read it from there
        var properties = new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withInitializer(new org.springframework.boot.test.context.ConfigDataApplicationContextInitializer())
                .withUserConfiguration(BookLookupConfiguration.class).withBean(Clock.class, Clock::systemUTC);
        properties.run(context -> assertThat(context.getBean(BookLookup.class)).isInstanceOf(NoBookLookup.class));
    }
}
