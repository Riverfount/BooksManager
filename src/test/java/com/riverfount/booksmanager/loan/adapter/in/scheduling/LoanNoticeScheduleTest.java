package com.riverfount.booksmanager.loan.adapter.in.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.riverfount.booksmanager.loan.application.port.in.SendLoanNoticesUseCase;
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
 * The schedule of the loan notices (RF28): the settings are read from the application properties, the time is read in
 * the declared zone and not in the one of the container, and the routine exists only with its own switch on and the
 * e-mail on (with the mail off nothing can be sent, and recording the notices as sent would lose them). Checked on a
 * light context (no database), with the real {@code application.properties} loaded and only the switches turned on,
 * since the tests turn the routine off.
 */
class LoanNoticeScheduleTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(UserConfigurations.of(LoanNoticeJob.class))
            .withBean(SendLoanNoticesUseCase.class, () -> mock(SendLoanNoticesUseCase.class));

    /** The next time the (only) scheduled task would fire, looking from {@code now}. */
    private static Instant nextRunAfter(org.springframework.context.ApplicationContext context, Instant now) {
        var tasks = context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks();
        assertThat(tasks).hasSize(1);
        var task = (CronTask) tasks.iterator().next().getTask();
        return task.getTrigger().nextExecution(new SimpleTriggerContext(Clock.fixed(now, ZoneOffset.UTC)));
    }

    @Test
    void shouldDefaultToEightInTheMorningOfSaoPauloWhateverTheZoneOfTheContainer() {
        runner.withPropertyValues("app.loan-notices.enabled=true", "app.mail.enabled=true").run(context -> {
            // 07:30 in São Paulo (UTC-3): the next run is at 08:00 of that day, which is 11:00 UTC
            assertThat(nextRunAfter(context, Instant.parse("2026-11-10T10:30:00Z")))
                    .isEqualTo(Instant.parse("2026-11-10T11:00:00Z"));
            // 09:00 in São Paulo: already past, so the next run is at 08:00 of the next day
            assertThat(nextRunAfter(context, Instant.parse("2026-11-10T12:00:00Z")))
                    .isEqualTo(Instant.parse("2026-11-11T11:00:00Z"));
        });
    }

    @Test
    void shouldReadTheTimeAndTheZoneFromTheProperties() {
        runner.withPropertyValues("app.loan-notices.enabled=true", "app.mail.enabled=true",
                "app.loan-notices.cron=0 30 9 * * *", "app.loan-notices.zone=UTC")
                .run(context -> assertThat(nextRunAfter(context, Instant.parse("2026-11-10T00:00:00Z")))
                        .isEqualTo(Instant.parse("2026-11-10T09:30:00Z")));
    }

    @Test
    void shouldBeOnWhenItsSwitchAndTheMailAreOn() {
        runner.withPropertyValues("app.loan-notices.enabled=true", "app.mail.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(LoanNoticeJob.class));
    }

    @Test
    void shouldBeOnByDefaultAsLongAsTheMailIsOn() {
        new ApplicationContextRunner()
                .withConfiguration(UserConfigurations.of(LoanNoticeJob.class))
                .withBean(SendLoanNoticesUseCase.class, () -> mock(SendLoanNoticesUseCase.class))
                .withPropertyValues("app.mail.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(LoanNoticeJob.class));
    }

    @Test
    void shouldBeOffWhenTheMailIsOffBecauseNothingCouldBeSent() {
        runner.withPropertyValues("app.loan-notices.enabled=true", "app.mail.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(LoanNoticeJob.class);
            assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
        });
        new ApplicationContextRunner()
                .withConfiguration(UserConfigurations.of(LoanNoticeJob.class))
                .withBean(SendLoanNoticesUseCase.class, () -> mock(SendLoanNoticesUseCase.class))
                .run(context -> assertThat(context).doesNotHaveBean(LoanNoticeJob.class));
    }

    @Test
    void shouldBeOffWhenItsSwitchIsOff() {
        runner.withPropertyValues("app.loan-notices.enabled=false", "app.mail.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(LoanNoticeJob.class));
    }

    @Test
    void theTestProfileShouldHaveItOffByDefault() {
        // the runner loads config/application.properties of the tests, which turns the routine and the mail off
        runner.run(context -> assertThat(context).doesNotHaveBean(LoanNoticeJob.class));
    }
}
