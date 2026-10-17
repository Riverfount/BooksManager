package com.riverfount.booksmanager.loan.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PenaltyTest {

    @Test
    void nonePenaltyHasNothingToApply() {
        var none = Penalty.none();

        assertThat(none.fineAmount()).isEqualByComparingTo("0");
        assertThat(none.suspensionDays()).isZero();
        assertThat(none.hasFine()).isFalse();
        assertThat(none.hasSuspension()).isFalse();
        assertThat(none.isNone()).isTrue();
    }

    @Test
    void shouldTellWhatItHolds() {
        var both = new Penalty(new BigDecimal("3.00"), 3);
        var fineOnly = new Penalty(new BigDecimal("3.00"), 0);
        var suspensionOnly = new Penalty(BigDecimal.ZERO, 3);

        assertThat(both.hasFine()).isTrue();
        assertThat(both.hasSuspension()).isTrue();
        assertThat(both.isNone()).isFalse();
        assertThat(fineOnly.hasFine()).isTrue();
        assertThat(fineOnly.hasSuspension()).isFalse();
        assertThat(suspensionOnly.hasFine()).isFalse();
        assertThat(suspensionOnly.hasSuspension()).isTrue();
    }

    @Test
    void aZeroFineIsNotAFine() {
        // the Fine entity needs a positive amount, so a zero fine must never look like one
        assertThat(new Penalty(new BigDecimal("0.00"), 2).hasFine()).isFalse();
    }

    @Test
    void shouldKeepTheMoneyWithTwoDecimalPlaces() {
        assertThat(new Penalty(new BigDecimal("1.5"), 0).fineAmount().toPlainString()).isEqualTo("1.50");
        assertThat(Penalty.none().fineAmount().toPlainString()).isEqualTo("0.00");
    }

    @Test
    void shouldRefuseANegativeAmountOrDays() {
        assertThatThrownBy(() -> new Penalty(new BigDecimal("-0.01"), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Penalty(BigDecimal.ZERO, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Penalty(null, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
