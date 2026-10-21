package com.riverfount.booksmanager.loan.adapter.in.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import com.riverfount.booksmanager.loan.application.port.in.ExpireReservationsUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** The trigger only calls the use case and logs (RF19): the rule and the idempotence are the use case's. */
@ExtendWith(OutputCaptureExtension.class)
class ReservationExpirationJobTest {

    private ExpireReservationsUseCase useCase;
    private ReservationExpirationJob job;

    @BeforeEach
    void setUp() {
        useCase = mock(ExpireReservationsUseCase.class);
        job = new ReservationExpirationJob(useCase);
    }

    @Test
    void theScheduledRunShouldCallTheUseCaseAndLogHowManyWereExpired(CapturedOutput output) {
        given(useCase.expireOverdue()).willReturn(3);

        job.expireOnSchedule();

        then(useCase).should().expireOverdue();
        assertThat(output).contains("Expiration of reservations (scheduled): 3 expired.");
    }

    @Test
    void theStartupRunShouldCallTheSameUseCase(CapturedOutput output) {
        given(useCase.expireOverdue()).willReturn(0);

        job.expireOnStartup();

        then(useCase).should().expireOverdue();
        assertThat(output).contains("Expiration of reservations (startup): 0 expired.");
    }

    @Test
    void aFailureShouldBeLoggedAndNotStopTheSchedulerNorTheStartup(CapturedOutput output) {
        given(useCase.expireOverdue()).willThrow(new IllegalStateException("database down"));

        assertThatCode(job::expireOnSchedule).doesNotThrowAnyException();
        assertThatCode(job::expireOnStartup).doesNotThrowAnyException();

        then(useCase).should(times(2)).expireOverdue();
        assertThat(output).contains("Expiration of reservations (scheduled) failed")
                .contains("Expiration of reservations (startup) failed");
    }

    @Test
    void thereShouldBeNoRuleInTheTriggerSoEachRunCallsTheUseCaseOnce() {
        job.expireOnSchedule();
        job.expireOnSchedule();

        then(useCase).should(times(2)).expireOverdue();
    }
}
