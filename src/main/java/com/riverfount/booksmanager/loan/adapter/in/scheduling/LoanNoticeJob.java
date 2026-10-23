package com.riverfount.booksmanager.loan.adapter.in.scheduling;

import com.riverfount.booksmanager.loan.application.port.in.SendLoanNoticesUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The trigger of the loan notices (RF28): every morning at the configured time, in the configured time zone (08:00 in
 * {@code America/Sao_Paulo} by default, declared here so it does not depend on the zone of the container), and once
 * when the application is ready, to recover a day that was missed because the application was down at that time. It
 * holds no rule: the work, and the idempotence that makes running it again harmless (what was sent is recorded), are
 * the use case's. A failure is logged and swallowed, so it neither stops the scheduler nor the startup.
 *
 * <p>It exists only while {@code app.loan-notices.enabled} is true (the default) <em>and</em> the e-mail is on
 * ({@code app.mail.enabled}): with the mail off nothing can be sent, and recording the notices as sent would lose them
 * for good. The tests turn it off, so no test fires it by accident.
 */
@Component
@EnableScheduling
@ConditionalOnExpression("${app.loan-notices.enabled:true} and ${app.mail.enabled:false}")
public class LoanNoticeJob {

    private static final Logger log = LoggerFactory.getLogger(LoanNoticeJob.class);

    private final SendLoanNoticesUseCase sendLoanNoticesUseCase;

    LoanNoticeJob(SendLoanNoticesUseCase sendLoanNoticesUseCase) {
        this.sendLoanNoticesUseCase = sendLoanNoticesUseCase;
    }

    @Scheduled(cron = "${app.loan-notices.cron:0 0 8 * * *}",
            zone = "${app.loan-notices.zone:America/Sao_Paulo}")
    public void sendOnSchedule() {
        run("scheduled");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void sendOnStartup() {
        run("startup");
    }

    private void run(String trigger) {
        try {
            var sent = sendLoanNoticesUseCase.sendNotices();
            log.info("Loan notices ({}): {} sent.", trigger, sent);
        } catch (RuntimeException e) {
            log.error("Loan notices ({}) failed; they will run again at the next time.", trigger, e);
        }
    }
}
