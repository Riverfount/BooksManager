package com.riverfount.booksmanager.loan.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The reservation and its transitions (RF16, RF17, RF19, RN13); the date always comes by parameter. */
class ReservationTest {

    private static final LocalDate RESERVED_ON = LocalDate.of(2026, 10, 19);
    private static final LocalDate ASSIGNED_ON = LocalDate.of(2026, 10, 25);
    private static final int PICKUP_DAYS = 3;
    private static final LocalDate DEADLINE = ASSIGNED_ON.plusDays(PICKUP_DAYS); // 2026-10-28

    private static Reservation waiting() {
        return Reservation.create(30L, 20L, RESERVED_ON);
    }

    private static Reservation holding() {
        var reservation = waiting();
        reservation.assignCopy(10L, ASSIGNED_ON, PICKUP_DAYS);
        return reservation;
    }

    // creation (RF16)

    @Test
    void shouldCreateAnActiveReservationWaitingInTheQueue() {
        var reservation = waiting();

        assertThat(reservation.getId()).isNull();
        assertThat(reservation.getBookId()).isEqualTo(30L);
        assertThat(reservation.getReaderId()).isEqualTo(20L);
        assertThat(reservation.getReservationDate()).isEqualTo(RESERVED_ON);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(reservation.getCopyId()).isNull();
        assertThat(reservation.getPickupDeadline()).isNull();
        assertThat(reservation.isActive()).isTrue();
        assertThat(reservation.isWaiting()).isTrue();
        assertThat(reservation.isHoldingCopy()).isFalse();
    }

    @Test
    void shouldRequireTheBookTheReaderAndTheDate() {
        assertThatThrownBy(() -> Reservation.create(null, 20L, RESERVED_ON))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("livro");
        assertThatThrownBy(() -> Reservation.create(30L, null, RESERVED_ON))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("leitor");
        assertThatThrownBy(() -> Reservation.create(30L, 20L, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("data");
    }

    // holding a copy (RN13)

    @Test
    void shouldHoldACopyWithADeadlineOfTheConfiguredDays() {
        var reservation = holding();

        assertThat(reservation.getCopyId()).isEqualTo(10L);
        assertThat(reservation.getPickupDeadline()).isEqualTo(DEADLINE);
        assertThat(reservation.isHoldingCopy()).isTrue();
        assertThat(reservation.isWaiting()).isFalse();
        assertThat(reservation.isActive()).isTrue();
    }

    @Test
    void theDeadlineFollowsThePickupDaysGivenNotAConstant() {
        var seven = waiting();
        seven.assignCopy(10L, ASSIGNED_ON, 7);
        var one = waiting();
        one.assignCopy(10L, ASSIGNED_ON, 1);

        assertThat(seven.getPickupDeadline()).isEqualTo(ASSIGNED_ON.plusDays(7));
        assertThat(one.getPickupDeadline()).isEqualTo(ASSIGNED_ON.plusDays(1));
    }

    @Test
    void theDeadlineCrossesMonthYearAndLeapDayCorrectly() {
        var year = waiting();
        year.assignCopy(10L, LocalDate.of(2026, 12, 30), 3);
        var leap = waiting();
        leap.assignCopy(10L, LocalDate.of(2028, 2, 27), 3);

        assertThat(year.getPickupDeadline()).isEqualTo(LocalDate.of(2027, 1, 2));
        assertThat(leap.getPickupDeadline()).isEqualTo(LocalDate.of(2028, 3, 1));
    }

    @Test
    void shouldRefuseToAssignWithoutCopyDateOrAPositivePeriod() {
        assertThatThrownBy(() -> waiting().assignCopy(null, ASSIGNED_ON, PICKUP_DAYS))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("exemplar");
        assertThatThrownBy(() -> waiting().assignCopy(10L, null, PICKUP_DAYS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> waiting().assignCopy(10L, ASSIGNED_ON, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldNotAssignASecondCopyToAReservationThatAlreadyHoldsOne() {
        var reservation = holding();

        assertThatThrownBy(() -> reservation.assignCopy(11L, ASSIGNED_ON.plusDays(1), PICKUP_DAYS))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("já segura");
        assertThat(reservation.getCopyId()).isEqualTo(10L);
        assertThat(reservation.getPickupDeadline()).isEqualTo(DEADLINE);
    }

    @Test
    void shouldNotAssignACopyBeforeTheReservationWasMade() {
        var reservation = waiting();

        assertThatThrownBy(() -> reservation.assignCopy(10L, RESERVED_ON.minusDays(1), PICKUP_DAYS))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("anterior");
        assertThat(reservation.isWaiting()).isTrue();
    }

    // fulfilling (RF17): the deadline is inclusive

    @Test
    void shouldFulfillOnTheDayTheCopyWasAssigned() {
        var reservation = holding();

        reservation.fulfill(ASSIGNED_ON);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.FULFILLED);
        assertThat(reservation.isActive()).isFalse();
    }

    @Test
    void shouldFulfillOnTheLastDayOfTheDeadline() {
        var reservation = holding();

        reservation.fulfill(DEADLINE);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.FULFILLED);
    }

    @Test
    void shouldNotFulfillTheDayAfterTheDeadline() {
        var reservation = holding();

        assertThatThrownBy(() -> reservation.fulfill(DEADLINE.plusDays(1)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("prazo");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test
    void shouldNotFulfillAReservationStillWaitingInTheQueue() {
        var reservation = waiting();

        assertThatThrownBy(() -> reservation.fulfill(ASSIGNED_ON))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("fila");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test
    void shouldRequireTheDateToFulfill() {
        assertThatThrownBy(() -> holding().fulfill(null)).isInstanceOf(IllegalArgumentException.class);
    }

    // cancelling (RF17)

    @Test
    void shouldCancelAReservationWaitingInTheQueue() {
        var reservation = waiting();

        reservation.cancel();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservation.isActive()).isFalse();
    }

    @Test
    void shouldCancelAReservationHoldingACopyKeepingTheCopyForTheHistory() {
        var reservation = holding();

        reservation.cancel();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservation.getCopyId()).isEqualTo(10L);
        assertThat(reservation.isHoldingCopy()).isFalse();
    }

    // expiring (RF19): only after the deadline

    @Test
    void shouldNotExpireOnTheLastDayOfTheDeadline() {
        var reservation = holding();

        assertThatThrownBy(() -> reservation.expire(DEADLINE))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("prazo");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test
    void shouldExpireTheDayAfterTheDeadline() {
        var reservation = holding();

        reservation.expire(DEADLINE.plusDays(1));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(reservation.isActive()).isFalse();
        assertThat(reservation.getCopyId()).isEqualTo(10L);
    }

    @Test
    void shouldNotExpireAReservationWaitingInTheQueue() {
        var reservation = waiting();

        assertThatThrownBy(() -> reservation.expire(ASSIGNED_ON.plusDays(365)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("fila");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test
    void shouldRequireTheDateToExpire() {
        assertThatThrownBy(() -> holding().expire(null)).isInstanceOf(IllegalArgumentException.class);
    }

    // derived: past the deadline

    @Test
    void isPastDeadlineOnlyAfterTheLastDayAndOnlyWhileHoldingACopy() {
        var reservation = holding();

        assertThat(reservation.isPastDeadline(DEADLINE)).isFalse();
        assertThat(reservation.isPastDeadline(DEADLINE.plusDays(1))).isTrue();
        assertThat(waiting().isPastDeadline(DEADLINE.plusDays(100))).isFalse();
    }

    // closed reservations never change again

    private static Reservation closed(ReservationStatus status) {
        var reservation = holding();
        switch (status) {
            case FULFILLED -> reservation.fulfill(DEADLINE);
            case CANCELLED -> reservation.cancel();
            case EXPIRED -> reservation.expire(DEADLINE.plusDays(1));
            default -> throw new IllegalArgumentException(status.name());
        }
        return reservation;
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"FULFILLED", "CANCELLED", "EXPIRED"})
    void aClosedReservationCannotBeChanged(ReservationStatus status) {
        var reservation = closed(status);

        assertThatThrownBy(() -> reservation.fulfill(DEADLINE)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("encerrada");
        assertThatThrownBy(reservation::cancel).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("encerrada");
        assertThatThrownBy(() -> reservation.expire(DEADLINE.plusDays(5))).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("encerrada");
        assertThatThrownBy(() -> reservation.assignCopy(11L, ASSIGNED_ON, PICKUP_DAYS))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("encerrada");
        assertThat(reservation.getStatus()).isEqualTo(status);
        assertThat(reservation.getCopyId()).isEqualTo(10L);
        assertThat(reservation.getPickupDeadline()).isEqualTo(DEADLINE);
    }

    @Test
    void aCancelledReservationThatWasWaitingCannotBeChangedEither() {
        var reservation = waiting();
        reservation.cancel();

        assertThatThrownBy(() -> reservation.assignCopy(10L, ASSIGNED_ON, PICKUP_DAYS))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("encerrada");
        assertThat(reservation.getCopyId()).isNull();
    }

    // reconstitution

    @Test
    void shouldReconstituteAPersistedReservation() {
        var reservation = Reservation.reconstitute(5L, 30L, 20L, RESERVED_ON, ReservationStatus.ACTIVE, 10L, DEADLINE);

        assertThat(reservation.getId()).isEqualTo(5L);
        assertThat(reservation.isHoldingCopy()).isTrue();
        assertThat(reservation.getPickupDeadline()).isEqualTo(DEADLINE);
    }

    @Test
    void shouldReconstituteAWaitingReservation() {
        var reservation = Reservation.reconstitute(5L, 30L, 20L, RESERVED_ON, ReservationStatus.ACTIVE, null, null);

        assertThat(reservation.isWaiting()).isTrue();
    }

    @Test
    void shouldRefuseInconsistentPersistedData() {
        assertThatThrownBy(() -> Reservation.reconstitute(5L, 30L, 20L, RESERVED_ON, null, null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("situação");
        assertThatThrownBy(
                () -> Reservation.reconstitute(5L, 30L, 20L, RESERVED_ON, ReservationStatus.ACTIVE, 10L, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("prazo");
        assertThatThrownBy(
                () -> Reservation.reconstitute(5L, 30L, 20L, RESERVED_ON, ReservationStatus.ACTIVE, null, DEADLINE))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("prazo");
        assertThatThrownBy(
                () -> Reservation.reconstitute(5L, 30L, 20L, RESERVED_ON, ReservationStatus.FULFILLED, null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("exemplar");
        assertThatThrownBy(
                () -> Reservation.reconstitute(5L, 30L, 20L, RESERVED_ON, ReservationStatus.EXPIRED, null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("exemplar");
        assertThatThrownBy(() -> Reservation.reconstitute(5L, null, 20L, RESERVED_ON, ReservationStatus.ACTIVE, null,
                null)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("livro");
        assertThatThrownBy(() -> Reservation.reconstitute(5L, 30L, 20L, null, ReservationStatus.ACTIVE, null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("data");
        assertThatThrownBy(() -> Reservation.reconstitute(5L, 30L, 20L, RESERVED_ON, ReservationStatus.ACTIVE, 10L,
                RESERVED_ON.minusDays(1))).isInstanceOf(BusinessRuleException.class).hasMessageContaining("prazo");
    }

    @Test
    void shouldAcceptACancelledReservationThatNeverHeldACopy() {
        var reservation = Reservation.reconstitute(5L, 30L, 20L, RESERVED_ON, ReservationStatus.CANCELLED, null, null);

        assertThat(reservation.isActive()).isFalse();
    }
}
