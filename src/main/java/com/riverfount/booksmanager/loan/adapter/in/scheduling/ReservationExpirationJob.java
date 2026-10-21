package com.riverfount.booksmanager.loan.adapter.in.scheduling;

import com.riverfount.booksmanager.loan.application.port.in.ExpireReservationsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The trigger of the expiration of reservations (RF19, RN13): every day at the configured time, in the
 * configured time zone (03:00 in {@code America/Sao_Paulo} by default, declared here so it does not depend on the
 * zone of the container), and once when the application is ready, to recover a day that was missed because the
 * application was down at that time. It holds no rule: the work, and the idempotence that makes running it again
 * harmless, are the use case's. It logs how many reservations were expired, and a failure is logged and
 * swallowed, so it neither stops the scheduler nor the startup; the next run finds what was left.
 *
 * <p>It exists only while {@code app.reservations.expiration.enabled} is true (the default); the tests turn it off,
 * so no test fires it by accident.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "app.reservations.expiration", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class ReservationExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpirationJob.class);

    private final ExpireReservationsUseCase expireReservationsUseCase;

    ReservationExpirationJob(ExpireReservationsUseCase expireReservationsUseCase) {
        this.expireReservationsUseCase = expireReservationsUseCase;
    }

    @Scheduled(cron = "${app.reservations.expiration.cron:0 0 3 * * *}",
            zone = "${app.reservations.expiration.zone:America/Sao_Paulo}")
    public void expireOnSchedule() {
        run("scheduled");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void expireOnStartup() {
        run("startup");
    }

    private void run(String trigger) {
        try {
            var expired = expireReservationsUseCase.expireOverdue();
            log.info("Expiration of reservations ({}): {} expired.", trigger, expired);
        } catch (RuntimeException e) {
            log.error("Expiration of reservations ({}) failed; it will run again at the next time.", trigger, e);
        }
    }
}
