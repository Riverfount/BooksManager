package com.riverfount.booksmanager.shared.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** "Today" for loans and returns is the day in the library's timezone, whatever the server's (decision of #135). */
class ClockConfigurationTest {

    @Test
    void theApplicationClockShouldRunInTheSaoPauloTimezone() {
        var clock = new ClockConfiguration().clock();

        assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/Sao_Paulo"));
    }
}
