package com.riverfount.booksmanager.loan.adapter.in.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import com.riverfount.booksmanager.loan.application.port.in.SendLoanNoticesUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** The trigger only calls the use case and logs (RF28): the rule and the idempotence are the use case's. */
@ExtendWith(OutputCaptureExtension.class)
class LoanNoticeJobTest {

    private SendLoanNoticesUseCase useCase;
    private LoanNoticeJob job;

    @BeforeEach
    void setUp() {
        useCase = mock(SendLoanNoticesUseCase.class);
        job = new LoanNoticeJob(useCase);
    }

    @Test
    void theScheduledRunShouldCallTheUseCaseAndLogHowManyWereSent(CapturedOutput output) {
        given(useCase.sendNotices()).willReturn(3);

        job.sendOnSchedule();

        then(useCase).should().sendNotices();
        assertThat(output).contains("Loan notices (scheduled): 3 sent.");
    }

    @Test
    void theStartupRunShouldCallTheSameUseCase(CapturedOutput output) {
        given(useCase.sendNotices()).willReturn(0);

        job.sendOnStartup();

        then(useCase).should().sendNotices();
        assertThat(output).contains("Loan notices (startup): 0 sent.");
    }

    @Test
    void aFailureShouldBeLoggedAndNotStopTheSchedulerNorTheStartup(CapturedOutput output) {
        given(useCase.sendNotices()).willThrow(new IllegalStateException("database down"));

        assertThatCode(job::sendOnSchedule).doesNotThrowAnyException();
        assertThatCode(job::sendOnStartup).doesNotThrowAnyException();

        then(useCase).should(times(2)).sendNotices();
        assertThat(output).contains("Loan notices (scheduled) failed")
                .contains("Loan notices (startup) failed");
    }

    @Test
    void thereShouldBeNoRuleInTheTriggerSoEachRunCallsTheUseCaseOnce() {
        job.sendOnSchedule();
        job.sendOnSchedule();

        then(useCase).should(times(2)).sendNotices();
    }
}
