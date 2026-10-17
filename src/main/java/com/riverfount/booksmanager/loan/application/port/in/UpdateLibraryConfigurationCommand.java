package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.loan.domain.PenaltyPolicy;
import java.math.BigDecimal;

/**
 * Partial update of the library configuration (RF26, HU13): a null field means
 * "keep the current value".
 */
public record UpdateLibraryConfigurationCommand(
        PenaltyPolicy penaltyPolicy,
        BigDecimal dailyFineAmount,
        BigDecimal fineCap,
        Integer suspensionDaysPerDay,
        Integer suspensionCapDays,
        Integer loanPeriodDays,
        Integer loanLimit,
        Integer renewalLimit,
        Integer reservationPickupDays) {
}
