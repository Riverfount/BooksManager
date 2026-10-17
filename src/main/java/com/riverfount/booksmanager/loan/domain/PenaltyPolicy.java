package com.riverfount.booksmanager.loan.domain;

import java.math.BigDecimal;

/**
 * What the library does with a loan returned late (RF20, RN08): charge a fine,
 * suspend the reader, both, or nothing. The library chooses it in its
 * configuration (RN16), so changing it is never a change of code: the return calls
 * {@code configuration.getPenaltyPolicy().calculate(daysLate, configuration)} and the
 * variant stored there decides.
 *
 * <p>The accrual is per day of delay, up to the cap (decided on 17/10/2026): the fine
 * is the days late times the daily amount and the suspension is the days late times the
 * days per day of delay, each limited to its own cap. A variant uses only the numbers of
 * what it applies, so the fine numbers do not matter to {@link #SUSPENSION} and the
 * suspension numbers do not matter to {@link #FINE}. No delay (zero or negative days)
 * never costs anything.
 *
 * <p>The numbers are read from the configuration given, never from constants, and the
 * calculation does not read the clock: the days late come by parameter.
 */
public enum PenaltyPolicy {
    FINE(true, false),
    SUSPENSION(false, true),
    BOTH(true, true),
    NONE(false, false);

    private final boolean fines;
    private final boolean suspends;

    PenaltyPolicy(boolean fines, boolean suspends) {
        this.fines = fines;
        this.suspends = suspends;
    }

    /**
     * The penalty for a return {@code daysLate} days after the due date, by this
     * variant and the numbers of the configuration.
     *
     * @param daysLate whole calendar days past the due date; zero or negative means on time
     * @throws IllegalArgumentException when the configuration is missing
     */
    public Penalty calculate(long daysLate, LibraryConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("the library configuration is required");
        }
        if (daysLate <= 0) {
            return Penalty.none();
        }
        var fine = fines ? fineFor(daysLate, configuration) : BigDecimal.ZERO;
        var suspension = suspends ? suspensionDaysFor(daysLate, configuration) : 0;
        return new Penalty(fine, suspension);
    }

    private static BigDecimal fineFor(long daysLate, LibraryConfiguration configuration) {
        var accrued = configuration.getDailyFineAmount().multiply(BigDecimal.valueOf(daysLate));
        return accrued.min(configuration.getFineCap());
    }

    /**
     * Days late times the days per day, up to the cap, without ever overflowing: from the
     * first day count that reaches the cap on, the cap is returned before multiplying.
     */
    private static int suspensionDaysFor(long daysLate, LibraryConfiguration configuration) {
        long perDay = configuration.getSuspensionDaysPerDay();
        long cap = configuration.getSuspensionCapDays();
        var daysToReachTheCap = (cap + perDay - 1) / perDay;
        if (daysLate >= daysToReachTheCap) {
            return (int) cap;
        }
        return (int) (daysLate * perDay);
    }
}
