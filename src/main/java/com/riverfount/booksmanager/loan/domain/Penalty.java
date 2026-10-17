package com.riverfount.booksmanager.loan.domain;

import java.math.BigDecimal;

/**
 * What a loan returned late costs the reader (RF20, RN08): a fine, a suspension, both
 * or nothing, as decided by {@link PenaltyPolicy}. It is only the calculated result;
 * turning it into a {@code Fine} and a {@code Suspension} is up to the return use case.
 * The fine is kept with two decimal places, like the money in the configuration.
 *
 * @param fineAmount the fine in reais, zero when there is none
 * @param suspensionDays how many days the reader is suspended, zero when there is none
 */
public record Penalty(BigDecimal fineAmount, int suspensionDays) {

    private static final int MONEY_SCALE = 2;
    private static final Penalty NONE = new Penalty(BigDecimal.ZERO, 0);

    public Penalty {
        if (fineAmount == null || fineAmount.signum() < 0) {
            throw new IllegalArgumentException("the fine amount must be zero or more");
        }
        if (suspensionDays < 0) {
            throw new IllegalArgumentException("the suspension days must be zero or more");
        }
        if (fineAmount.stripTrailingZeros().scale() > MONEY_SCALE) {
            throw new IllegalArgumentException("the fine amount has at most two decimal places");
        }
        fineAmount = fineAmount.setScale(MONEY_SCALE);
    }

    public static Penalty none() {
        return NONE;
    }

    /** Whether there is a fine to charge: a zero fine is not a fine. */
    public boolean hasFine() {
        return fineAmount.signum() > 0;
    }

    public boolean hasSuspension() {
        return suspensionDays > 0;
    }

    public boolean isNone() {
        return !hasFine() && !hasSuspension();
    }
}
