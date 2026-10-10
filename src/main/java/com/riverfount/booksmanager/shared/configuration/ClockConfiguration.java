package com.riverfount.booksmanager.shared.configuration;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the {@code Clock} used by application services, so that the
 * domain never needs to call {@code LocalDate.now()} directly. In
 * production it is the real system clock; tests use
 * {@code Clock.fixed(...)} and do not depend on this bean.
 */
@Configuration
public class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
