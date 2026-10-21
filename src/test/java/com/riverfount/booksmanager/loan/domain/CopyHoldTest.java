package com.riverfount.booksmanager.loan.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** What a reserved copy means for the reader who asks to borrow it (RN03, RN13). */
class CopyHoldTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 1);
    private static final long OWNER = 20L;

    /** A reservation of {@code OWNER} that holds a copy until DAY + 3 (inclusive). */
    private static Reservation holding() {
        var reservation = Reservation.create(30L, OWNER, DAY.minusDays(2));
        reservation.assignCopy(7L, DAY, 3);
        return reservation;
    }

    @Test
    void theOwnerShouldGetTheCopyUpToTheLastDayOfTheDeadlineInclusive() {
        assertThat(CopyHold.of(holding(), OWNER, DAY)).isEqualTo(CopyHold.FOR_THIS_READER);
        assertThat(CopyHold.of(holding(), OWNER, DAY.plusDays(3))).isEqualTo(CopyHold.FOR_THIS_READER);
    }

    @Test
    void theOwnerShouldBeRefusedTheDayAfterTheDeadline() {
        assertThat(CopyHold.of(holding(), OWNER, DAY.plusDays(4))).isEqualTo(CopyHold.FOR_THIS_READER_PAST_DEADLINE);
    }

    @Test
    void anotherReaderShouldBeRefusedWhateverTheDate() {
        assertThat(CopyHold.of(holding(), 21L, DAY)).isEqualTo(CopyHold.FOR_ANOTHER_READER);
        assertThat(CopyHold.of(holding(), 21L, DAY.plusDays(10))).isEqualTo(CopyHold.FOR_ANOTHER_READER);
    }

    @Test
    void aReservedCopyThatNoActiveReservationHoldsIsForNobodyToTake() {
        assertThat(CopyHold.of(null, OWNER, DAY)).isEqualTo(CopyHold.FOR_ANOTHER_READER);
    }
}
