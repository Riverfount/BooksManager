package com.riverfount.booksmanager.loan.adapter.in.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.riverfount.booksmanager.loan.application.port.in.ExpireReservationsUseCase;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.support.SimpleTriggerContext;

/**
 * The schedule of the expiration (RF19): the settings are read from the application properties, the time is
 * read in the declared zone and not in the one of the container, and the routine can be turned off. It is checked
 * on a light context (no database), with the real {@code application.properties} loaded and only the switch
 * turned on, since the tests turn the routine off.
 */
class ReservationExpirationScheduleTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(UserConfigurations.of(ReservationExpirationJob.class))
            .withBean(ExpireReservationsUseCase.class, () -> mock(ExpireReservationsUseCase.class));

    /** The next time the (only) scheduled task would fire, looking from {@code now}. */
    private static Instant nextRunAfter(org.springframework.context.ApplicationContext context, Instant now) {
        var tasks = context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks();
        assertThat(tasks).hasSize(1);
        var task = (CronTask) tasks.iterator().next().getTask();
        return task.getTrigger().nextExecution(new SimpleTriggerContext(Clock.fixed(now, ZoneOffset.UTC)));
    }

    @Test
    void shouldDefaultToThreeInTheMorningOfSaoPauloWhateverTheZoneOfTheContainer() {
        runner.withPropertyValues("app.reservations.expiration.enabled=true").run(context -> {
            // 02:30 in São Paulo (UTC-3): the next run is at 03:00 of that day, which is 06:00 UTC
            assertThat(nextRunAfter(context, Instant.parse("2026-11-10T05:30:00Z")))
                    .isEqualTo(Instant.parse("2026-11-10T06:00:00Z"));
            // 04:00 in São Paulo: already past, so the next run is at 03:00 of the next day
            assertThat(nextRunAfter(context, Instant.parse("2026-11-10T07:00:00Z")))
                    .isEqualTo(Instant.parse("2026-11-11T06:00:00Z"));
        });
    }

    @Test
    void shouldReadTheTimeAndTheZoneFromTheProperties() {
        runner.withPropertyValues("app.reservations.expiration.enabled=true",
                "app.reservations.expiration.cron=0 30 4 * * *", "app.reservations.expiration.zone=UTC")
                .run(context -> assertThat(nextRunAfter(context, Instant.parse("2026-11-10T00:00:00Z")))
                        .isEqualTo(Instant.parse("2026-11-10T04:30:00Z")));
    }

    @Test
    void shouldBeOnWhenTheSwitchIsOnAndAlsoWhenItIsMissing() {
        runner.withPropertyValues("app.reservations.expiration.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(ReservationExpirationJob.class));
        // not set in the file of the tests' own config: the default of the property is on
        new ApplicationContextRunner()
                .withConfiguration(UserConfigurations.of(ReservationExpirationJob.class))
                .withBean(ExpireReservationsUseCase.class, () -> mock(ExpireReservationsUseCase.class))
                .run(context -> assertThat(context).hasSingleBean(ReservationExpirationJob.class));
    }

    @Test
    void shouldBeOffWhenTheSwitchIsOff() {
        runner.withPropertyValues("app.reservations.expiration.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(ReservationExpirationJob.class);
            assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
        });
    }

    @Test
    void theTestProfileShouldHaveItOffByDefault() {
        // the runner loads config/application.properties of the tests, which turns the routine off
        runner.run(context -> assertThat(context).doesNotHaveBean(ReservationExpirationJob.class));
    }
}
