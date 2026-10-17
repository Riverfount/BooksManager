package com.riverfount.booksmanager.shared.configuration;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the {@code Clock} used by application services, so that the
 * domain never needs to call {@code LocalDate.now()} directly. In
 * production it is the real system clock, set to the library's timezone
 * (America/Sao_Paulo, decision of #135) so that "today" is the same day for
 * the loans whatever the timezone of the server; tests use
 * {@code Clock.fixed(...)} and do not depend on this bean.
 */
@Configuration
public class ClockConfiguration {

    private static final ZoneId LIBRARY_ZONE = ZoneId.of("America/Sao_Paulo");

    @Bean
    Clock clock() {
        return Clock.system(LIBRARY_ZONE);
    }
}
