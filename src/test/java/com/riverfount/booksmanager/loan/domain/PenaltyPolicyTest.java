package com.riverfount.booksmanager.loan.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PenaltyPolicyTest {

    @Test
    void shouldHaveExactlyTheFourVariantsOfTheLatenessPenalty() {
        assertThat(PenaltyPolicy.values()).containsExactly(
                PenaltyPolicy.FINE, PenaltyPolicy.SUSPENSION, PenaltyPolicy.BOTH, PenaltyPolicy.NONE);
    }

    @Test
    void theNamesShouldMatchWhatTheDatabaseAccepts() {
        assertThat(PenaltyPolicy.values()).extracting(Enum::name)
                .containsExactly("FINE", "SUSPENSION", "BOTH", "NONE");
    }

    // The numbers come from the configuration (RN16). The initial ones (decided on 10/10/2026): fine of R$ 1,00 per
    // day capped at R$ 10,00; suspension of 1 day per day capped at 365 days.

    private static LibraryConfiguration initial() {
        return LibraryConfiguration.reconstitute(1L, PenaltyPolicy.FINE, new BigDecimal("1.00"),
                new BigDecimal("10.00"), 1, 365, 14, 3, 2, 3);
    }

    private static LibraryConfiguration configuration(String dailyFine, String fineCap, int daysPerDay, int cap) {
        return LibraryConfiguration.reconstitute(1L, PenaltyPolicy.FINE, new BigDecimal(dailyFine),
                new BigDecimal(fineCap), daysPerDay, cap, 14, 3, 2, 3);
    }

    // FINE

    @Test
    void fineChargesTheDailyAmountTimesTheDaysLateAndNothingElse() {
        var penalty = PenaltyPolicy.FINE.calculate(3, initial());

        assertThat(penalty.fineAmount()).isEqualByComparingTo("3.00");
        assertThat(penalty.suspensionDays()).isZero();
        assertThat(penalty.hasSuspension()).isFalse();
    }

    @Test
    void fineIsCappedAndTheExactCapIsNotCutShort() {
        var configuration = initial();

        assertThat(PenaltyPolicy.FINE.calculate(9, configuration).fineAmount()).isEqualByComparingTo("9.00");
        assertThat(PenaltyPolicy.FINE.calculate(10, configuration).fineAmount())
                .as("exactly at the cap").isEqualByComparingTo("10.00");
        assertThat(PenaltyPolicy.FINE.calculate(11, configuration).fineAmount())
                .as("one day past the cap").isEqualByComparingTo("10.00");
        assertThat(PenaltyPolicy.FINE.calculate(15, configuration).fineAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void fineWorksWithCentsAndACapThatIsNotAMultipleOfTheDailyAmount() {
        var configuration = configuration("0.50", "10.00", 1, 365);
        assertThat(PenaltyPolicy.FINE.calculate(3, configuration).fineAmount()).isEqualByComparingTo("1.50");
        assertThat(PenaltyPolicy.FINE.calculate(20, configuration).fineAmount()).isEqualByComparingTo("10.00");

        var odd = configuration("3.00", "10.00", 1, 365);
        assertThat(PenaltyPolicy.FINE.calculate(3, odd).fineAmount()).isEqualByComparingTo("9.00");
        assertThat(PenaltyPolicy.FINE.calculate(4, odd).fineAmount())
                .as("12.00 would pass the cap").isEqualByComparingTo("10.00");
    }

    @Test
    void fineIgnoresTheSuspensionNumbersEvenWhenTheyAreSet() {
        var penalty = PenaltyPolicy.FINE.calculate(5, configuration("1.00", "10.00", 7, 90));

        assertThat(penalty.suspensionDays()).isZero();
    }

    @Test
    void fineWithAZeroDailyAmountChargesNothing() {
        var penalty = PenaltyPolicy.FINE.calculate(5, configuration("0", "10.00", 1, 365));

        assertThat(penalty.hasFine()).isFalse();
        assertThat(penalty.isNone()).isTrue();
    }

    // SUSPENSION

    @Test
    void suspensionIsTheDaysLateTimesTheFactorAndNoFine() {
        var penalty = PenaltyPolicy.SUSPENSION.calculate(3, initial());

        assertThat(penalty.suspensionDays()).isEqualTo(3);
        assertThat(penalty.fineAmount()).isEqualByComparingTo("0");
        assertThat(penalty.hasFine()).isFalse();
    }

    @Test
    void suspensionIsCappedAndTheExactCapIsNotCutShort() {
        var configuration = initial();

        assertThat(PenaltyPolicy.SUSPENSION.calculate(364, configuration).suspensionDays()).isEqualTo(364);
        assertThat(PenaltyPolicy.SUSPENSION.calculate(365, configuration).suspensionDays())
                .as("exactly at the cap").isEqualTo(365);
        assertThat(PenaltyPolicy.SUSPENSION.calculate(366, configuration).suspensionDays())
                .as("one day past the cap").isEqualTo(365);
    }

    @Test
    void suspensionUsesTheConfiguredFactor() {
        var configuration = configuration("1.00", "10.00", 2, 90);

        assertThat(PenaltyPolicy.SUSPENSION.calculate(3, configuration).suspensionDays()).isEqualTo(6);
        assertThat(PenaltyPolicy.SUSPENSION.calculate(44, configuration).suspensionDays()).isEqualTo(88);
        assertThat(PenaltyPolicy.SUSPENSION.calculate(45, configuration).suspensionDays())
                .as("exactly at the cap").isEqualTo(90);
        assertThat(PenaltyPolicy.SUSPENSION.calculate(46, configuration).suspensionDays()).isEqualTo(90);
    }

    @Test
    void suspensionNeverPassesTheCapWhenTheFactorDoesNotDivideIt() {
        // 2 days per day against a cap of 365: 182 days make 364, and 183 would make 366, past the cap
        var configuration = configuration("1.00", "10.00", 2, 365);

        assertThat(PenaltyPolicy.SUSPENSION.calculate(182, configuration).suspensionDays()).isEqualTo(364);
        assertThat(PenaltyPolicy.SUSPENSION.calculate(183, configuration).suspensionDays()).isEqualTo(365);
        assertThat(PenaltyPolicy.SUSPENSION.calculate(184, configuration).suspensionDays()).isEqualTo(365);
    }

    @Test
    void suspensionIgnoresTheFineNumbersEvenWhenTheyAreSet() {
        var penalty = PenaltyPolicy.SUSPENSION.calculate(5, configuration("2.00", "50.00", 1, 365));

        assertThat(penalty.hasFine()).isFalse();
        assertThat(penalty.suspensionDays()).isEqualTo(5);
    }

    @Test
    void suspensionDoesNotOverflowWithAHugeDelayOrFactor() {
        var big = configuration("1.00", "10.00", Integer.MAX_VALUE, 365);

        assertThat(PenaltyPolicy.SUSPENSION.calculate(Long.MAX_VALUE, initial()).suspensionDays()).isEqualTo(365);
        assertThat(PenaltyPolicy.SUSPENSION.calculate(Long.MAX_VALUE, big).suspensionDays()).isEqualTo(365);
        assertThat(PenaltyPolicy.SUSPENSION.calculate(1_000_000, big).suspensionDays()).isEqualTo(365);
    }

    // BOTH

    @Test
    void bothAppliesTheFineAndTheSuspensionTogether() {
        var penalty = PenaltyPolicy.BOTH.calculate(3, initial());

        assertThat(penalty.fineAmount()).isEqualByComparingTo("3.00");
        assertThat(penalty.suspensionDays()).isEqualTo(3);
    }

    @Test
    void bothCapsEachOneOnItsOwn() {
        var configuration = configuration("1.00", "10.00", 1, 12);

        var atTheFineCap = PenaltyPolicy.BOTH.calculate(10, configuration);
        assertThat(atTheFineCap.fineAmount()).isEqualByComparingTo("10.00");
        assertThat(atTheFineCap.suspensionDays()).as("below its own cap").isEqualTo(10);

        var pastBoth = PenaltyPolicy.BOTH.calculate(20, configuration);
        assertThat(pastBoth.fineAmount()).isEqualByComparingTo("10.00");
        assertThat(pastBoth.suspensionDays()).isEqualTo(12);
    }

    // NONE

    @Test
    void noneNeverPenalizesWhateverTheDelayAndTheNumbers() {
        for (var days : new long[] {1, 5, 400, Long.MAX_VALUE}) {
            assertThat(PenaltyPolicy.NONE.calculate(days, initial()).isNone()).as("days %s", days).isTrue();
        }
    }

    // no delay, no penalty (any variant)

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -30, Long.MIN_VALUE})
    void noDelayGeneratesNoPenaltyInAnyVariant(long daysLate) {
        for (var policy : PenaltyPolicy.values()) {
            assertThat(policy.calculate(daysLate, initial()).isNone()).as("%s with %s days", policy, daysLate)
                    .isTrue();
        }
    }

    @Test
    void oneDayLateIsTheFirstDayOfPenalty() {
        assertThat(PenaltyPolicy.FINE.calculate(1, initial()).fineAmount()).isEqualByComparingTo("1.00");
        assertThat(PenaltyPolicy.SUSPENSION.calculate(1, initial()).suspensionDays()).isEqualTo(1);
    }

    // the configuration decides (RN16): changing it needs no change of code

    @Test
    void changingThePolicyInTheConfigurationChangesTheOutcomeWithTheSameCode() {
        var configuration = initial();

        var asFine = configuration.getPenaltyPolicy().calculate(4, configuration);
        configuration.update(PenaltyPolicy.SUSPENSION, null, null, null, null, null, null, null, null);
        var asSuspension = configuration.getPenaltyPolicy().calculate(4, configuration);
        configuration.update(PenaltyPolicy.BOTH, null, null, null, null, null, null, null, null);
        var asBoth = configuration.getPenaltyPolicy().calculate(4, configuration);
        configuration.update(PenaltyPolicy.NONE, null, null, null, null, null, null, null, null);
        var asNone = configuration.getPenaltyPolicy().calculate(4, configuration);

        assertThat(asFine.hasFine()).isTrue();
        assertThat(asFine.hasSuspension()).isFalse();
        assertThat(asSuspension.hasFine()).isFalse();
        assertThat(asSuspension.hasSuspension()).isTrue();
        assertThat(asBoth.hasFine()).isTrue();
        assertThat(asBoth.hasSuspension()).isTrue();
        assertThat(asNone.isNone()).isTrue();
    }

    @Test
    void changingTheNumbersInTheConfigurationChangesTheOutcome() {
        var configuration = initial();
        configuration.update(null, new BigDecimal("2.50"), new BigDecimal("20.00"), null, null, null, null, null,
                null);

        assertThat(PenaltyPolicy.FINE.calculate(3, configuration).fineAmount()).isEqualByComparingTo("7.50");
        assertThat(PenaltyPolicy.FINE.calculate(9, configuration).fineAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void shouldRequireTheConfiguration() {
        assertThatThrownBy(() -> PenaltyPolicy.FINE.calculate(3, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
