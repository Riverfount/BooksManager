package com.riverfount.booksmanager.loan.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The suspension of a reader for a late return (RF20, RN15): it starts on the day of the
 * return, runs for the days it was given, ends by itself on the calculated date and can be
 * ended earlier with a justification. Being in force is derived from the date, so nothing
 * has to run in the background.
 */
class SuspensionTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 5);

    private static Suspension suspension() {
        return Suspension.create(20L, 10L, START, 3); // suspended on Nov 5, 6 and 7; free again on Nov 8
    }

    // the day the reader is released

    @Test
    void theReleaseDateShouldBeTheCalculatedEndWhenNothingEndedItEarly() {
        assertThat(suspension().releaseDate()).isEqualTo(START.plusDays(3));
    }

    @Test
    void theReleaseDateShouldBeTheDayItWasEndedEarly() {
        var suspension = suspension();
        suspension.endEarly(START.plusDays(1), "Pardoned");

        assertThat(suspension.releaseDate()).isEqualTo(START.plusDays(1));
        assertThat(suspension.getEndDate()).as("the calculated end stays as history").isEqualTo(START.plusDays(3));
    }

    @Test
    void theReleaseDateShouldBeTheFirstDayTheReaderIsNotInForceAnymore() {
        var suspension = suspension();

        assertThat(suspension.isInForce(suspension.releaseDate().minusDays(1))).isTrue();
        assertThat(suspension.isInForce(suspension.releaseDate())).isFalse();
    }

    // creation

    @Test
    void shouldCreateASuspensionStartingOnTheReturnAndEndingAfterTheDays() {
        var suspension = suspension();

        assertThat(suspension.getId()).isNull();
        assertThat(suspension.getReaderId()).isEqualTo(20L);
        assertThat(suspension.getLoanId()).isEqualTo(10L);
        assertThat(suspension.getStartDate()).isEqualTo(START);
        assertThat(suspension.getEndDate()).isEqualTo(START.plusDays(3));
        assertThat(suspension.isEndedEarly()).isFalse();
        assertThat(suspension.getEndedEarlyOn()).isNull();
        assertThat(suspension.getEndedEarlyReason()).isNull();
    }

    @Test
    void theEndDateFollowsTheDaysGivenAcrossMonthsAndYears() {
        assertThat(Suspension.create(1L, 2L, LocalDate.of(2026, 12, 30), 5).getEndDate())
                .isEqualTo(LocalDate.of(2027, 1, 4));
        assertThat(Suspension.create(1L, 2L, START, 365).getEndDate()).isEqualTo(START.plusDays(365));
        assertThat(Suspension.create(1L, 2L, START, 1).getEndDate()).isEqualTo(START.plusDays(1));
    }

    @Test
    void shouldRequireTheReaderTheLoanAndTheStartDate() {
        assertThatThrownBy(() -> Suspension.create(null, 10L, START, 3))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("leitor");
        assertThatThrownBy(() -> Suspension.create(20L, null, START, 3))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("empréstimo");
        assertThatThrownBy(() -> Suspension.create(20L, 10L, null, 3))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("início");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void aSuspensionNeedsAtLeastOneDay(int days) {
        assertThatThrownBy(() -> Suspension.create(20L, 10L, START, days))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldBeBuiltFromThePenaltyOfThePolicy() {
        var penalty = PenaltyPolicy.SUSPENSION.calculate(4, LibraryConfiguration.reconstitute(1L,
                PenaltyPolicy.SUSPENSION, new java.math.BigDecimal("1.00"), new java.math.BigDecimal("10.00"), 1, 365,
                14, 3, 2, 3));

        var suspension = Suspension.create(20L, 10L, START, penalty.suspensionDays());

        assertThat(suspension.getEndDate()).isEqualTo(START.plusDays(4));
    }

    // in force by itself, with no job to end it (RN15)

    @Test
    void isNotInForceBeforeItStarts() {
        assertThat(suspension().isInForce(START.minusDays(1))).isFalse();
    }

    @Test
    void isInForceFromTheStartDayThroughTheLastDay() {
        var suspension = suspension();

        assertThat(suspension.isInForce(START)).as("the day of the return").isTrue();
        assertThat(suspension.isInForce(START.plusDays(1))).isTrue();
        assertThat(suspension.isInForce(START.plusDays(2))).as("the last day").isTrue();
    }

    @Test
    void endsByItselfOnTheCalculatedDate() {
        var suspension = suspension();

        assertThat(suspension.isInForce(START.plusDays(3))).as("the end date is already free").isFalse();
        assertThat(suspension.isInForce(START.plusDays(4))).isFalse();
        assertThat(suspension.isInForce(START.plusYears(5))).isFalse();
    }

    @Test
    void aOneDaySuspensionCoversOnlyTheDayOfTheReturn() {
        var suspension = Suspension.create(20L, 10L, START, 1);

        assertThat(suspension.isInForce(START)).isTrue();
        assertThat(suspension.isInForce(START.plusDays(1))).isFalse();
    }

    // ending early (RN15)

    @Test
    void shouldEndEarlyRecordingTheDayAndTheReason() {
        var suspension = suspension();

        var changed = suspension.endEarly(START.plusDays(1), "  Atraso justificado pela diretoria  ");

        assertThat(changed).isTrue();
        assertThat(suspension.isEndedEarly()).isTrue();
        assertThat(suspension.getEndedEarlyOn()).isEqualTo(START.plusDays(1));
        assertThat(suspension.getEndedEarlyReason()).isEqualTo("Atraso justificado pela diretoria");
        assertThat(suspension.getEndDate()).as("the calculated date is kept as history").isEqualTo(START.plusDays(3));
    }

    @Test
    void afterEndingEarlyTheReaderIsFreeFromThatDayOnButWasSuspendedBefore() {
        var suspension = suspension();
        suspension.endEarly(START.plusDays(1), "motivo");

        assertThat(suspension.isInForce(START)).isTrue();
        assertThat(suspension.isInForce(START.plusDays(1))).as("free on the day it ended").isFalse();
        assertThat(suspension.isInForce(START.plusDays(2))).isFalse();
    }

    @Test
    void canEndEarlyOnTheLastDayItself() {
        var suspension = suspension();

        assertThat(suspension.endEarly(START.plusDays(2), "motivo")).isTrue();
        assertThat(suspension.isInForce(START.plusDays(2))).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    void shouldRefuseToEndEarlyWithoutAReasonKeepingItInForce(String reason) {
        var suspension = suspension();

        assertThatThrownBy(() -> suspension.endEarly(START.plusDays(1), reason))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("justificativa");
        assertThat(suspension.isEndedEarly()).isFalse();
        assertThat(suspension.isInForce(START.plusDays(1))).isTrue();
    }

    @Test
    void theReasonHasALimitOfSize() {
        var suspension = suspension();

        assertThatThrownBy(() -> suspension.endEarly(START, "x".repeat(Suspension.MAX_REASON_LENGTH + 1)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("justificativa");
        assertThat(suspension.isEndedEarly()).isFalse();

        assertThat(suspension.endEarly(START, "x".repeat(Suspension.MAX_REASON_LENGTH))).isTrue();
    }

    @Test
    void endingASuspensionThatAlreadyEndedByItselfHasNoEffect() {
        var suspension = suspension();

        var changed = suspension.endEarly(START.plusDays(3), "tarde demais");

        assertThat(changed).isFalse();
        assertThat(suspension.isEndedEarly()).isFalse();
        assertThat(suspension.getEndedEarlyReason()).isNull();
    }

    @Test
    void endingASuspensionAlreadyEndedEarlyHasNoEffectAndKeepsTheFirstReason() {
        var suspension = suspension();
        suspension.endEarly(START.plusDays(1), "primeira");

        var changed = suspension.endEarly(START.plusDays(2), "segunda");

        assertThat(changed).isFalse();
        assertThat(suspension.getEndedEarlyOn()).isEqualTo(START.plusDays(1));
        assertThat(suspension.getEndedEarlyReason()).isEqualTo("primeira");
    }

    @Test
    void aMissingReasonIsStillRefusedEvenWhenThereIsNothingToEnd() {
        var suspension = suspension();

        // malformed input is refused whatever the state of the suspension
        assertThatThrownBy(() -> suspension.endEarly(START.plusDays(10), " "))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("justificativa");
    }

    @Test
    void shouldRefuseABadDate() {
        var suspension = suspension();

        assertThatThrownBy(() -> suspension.isInForce(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> suspension.endEarly(null, "motivo")).isInstanceOf(IllegalArgumentException.class);
    }

    // reconstitution

    @Test
    void shouldReconstituteBothKindsWithTheirState() {
        var running = Suspension.reconstitute(1L, 20L, 10L, START, START.plusDays(3), null, null);
        var ended = Suspension.reconstitute(2L, 20L, 10L, START, START.plusDays(3), START.plusDays(1), "motivo");

        assertThat(running.getId()).isEqualTo(1L);
        assertThat(running.isInForce(START)).isTrue();
        assertThat(ended.isEndedEarly()).isTrue();
        assertThat(ended.isInForce(START.plusDays(1))).isFalse();
        assertThat(ended.getEndedEarlyReason()).isEqualTo("motivo");
    }

    @Test
    void shouldRefuseAnInconsistentPersistedState() {
        var end = START.plusDays(3);

        assertThatThrownBy(() -> Suspension.reconstitute(1L, 20L, 10L, START, START, null, null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Suspension.reconstitute(1L, 20L, 10L, START, end, START.plusDays(1), null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Suspension.reconstitute(1L, 20L, 10L, START, end, START.plusDays(1), " "))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Suspension.reconstitute(1L, 20L, 10L, START, end, null, "motivo"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Suspension.reconstitute(1L, 20L, 10L, START, end, end.plusDays(1), "motivo"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Suspension.reconstitute(1L, null, 10L, START, end, null, null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Suspension.reconstitute(1L, 20L, 10L, null, end, null, null))
                .isInstanceOf(BusinessRuleException.class);
    }
}
