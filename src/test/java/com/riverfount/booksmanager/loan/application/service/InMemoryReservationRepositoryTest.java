package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.loan.application.port.out.ReservationFilter;
import com.riverfount.booksmanager.loan.domain.Reservation;
import com.riverfount.booksmanager.loan.domain.ReservationStatus;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** The fake must behave like the adapter, or the service tests that use it would prove nothing. */
class InMemoryReservationRepositoryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 1);
    private static final long BOOK = 30L;

    private final InMemoryReservationRepository reservations = new InMemoryReservationRepository();

    private Reservation reserve(long book, long reader) {
        return reservations.save(Reservation.create(book, reader, DAY));
    }

    private Reservation reserveAndHold(long book, long reader, long copy, LocalDate assignedOn) {
        var reservation = Reservation.create(book, reader, assignedOn);
        reservation.assignCopy(copy, assignedOn, 3);
        return reservations.save(reservation);
    }

    @Test
    void shouldGiveAnIdToANewReservationAndFindIt() {
        var saved = reserve(BOOK, 1L);

        assertThat(saved.getId()).isNotNull();
        assertThat(reservations.findById(saved.getId())).isPresent();
        assertThat(reservations.findById(-1L)).isEmpty();
    }

    @Test
    void aChangeToAReservationOnlyTakesEffectWhenItIsSaved() {
        var reservation = reserve(BOOK, 1L);
        reservation.cancel();

        assertThat(reservations.findById(reservation.getId()).orElseThrow().isActive()).isTrue();

        reservations.save(reservation);

        assertThat(reservations.findById(reservation.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void shouldRefuseASecondActiveReservationOfTheSameBookByTheSameReader() {
        reserve(BOOK, 1L);

        assertThatThrownBy(() -> reserve(BOOK, 1L)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("reserva ativa deste livro");
    }

    @Test
    void shouldAllowANewReservationOnceThePreviousOneIsClosed() {
        var first = reserve(BOOK, 1L);
        first.cancel();
        reservations.save(first);

        assertThat(reserve(BOOK, 1L).getId()).isNotEqualTo(first.getId());
    }

    @Test
    void shouldRefuseTwoActiveReservationsHoldingTheSameCopy() {
        reserveAndHold(BOOK, 1L, 10L, DAY);

        assertThatThrownBy(() -> reserveAndHold(BOOK, 2L, 10L, DAY)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exemplar");
    }

    @Test
    void theQueueIsTheWaitingReservationsOfTheBookInOrderOfArrival() {
        var first = reserve(BOOK, 1L);
        var second = reserve(BOOK, 2L);
        reserve(99L, 3L);

        assertThat(reservations.findFirstWaiting(BOOK)).map(Reservation::getId).contains(first.getId());
        assertThat(reservations.queuePosition(first.getId())).contains(1L);
        assertThat(reservations.queuePosition(second.getId())).contains(2L);

        first.assignCopy(10L, DAY, 3);
        reservations.save(first);

        assertThat(reservations.findFirstWaiting(BOOK)).map(Reservation::getId).contains(second.getId());
        assertThat(reservations.queuePosition(second.getId())).contains(1L);
        assertThat(reservations.queuePosition(first.getId())).isEmpty();
        assertThat(reservations.queuePosition(-1L)).isEmpty();
        assertThat(reservations.findFirstWaiting(-1L)).isEmpty();
    }

    @Test
    void shouldCountOnlyTheActiveReservationsOfTheReader() {
        reserve(BOOK, 1L);
        reserveAndHold(31L, 1L, 10L, DAY);
        var closed = reserve(32L, 1L);
        closed.cancel();
        reservations.save(closed);
        reserve(BOOK, 2L);

        assertThat(reservations.countActiveByReader(1L)).isEqualTo(2L);
        assertThat(reservations.countActiveByReader(-1L)).isZero();
    }

    @Test
    void shouldFindTheReservationsPastTheirDeadlineOldestFirst() {
        var lastDay = reserveAndHold(BOOK, 1L, 10L, DAY);                  // deadline 11-04
        var late = reserveAndHold(BOOK, 2L, 11L, DAY.minusDays(5));        // 10-30
        reserve(BOOK, 3L);
        var cancelled = reserveAndHold(BOOK, 4L, 12L, DAY.minusDays(9));
        cancelled.cancel();
        reservations.save(cancelled);

        assertThat(reservations.findPastDeadline(DAY.plusDays(3))).extracting(Reservation::getId)
                .containsExactly(late.getId());
        assertThat(reservations.findPastDeadline(DAY.plusDays(4))).extracting(Reservation::getId)
                .containsExactly(late.getId(), lastDay.getId());
    }

    @Test
    void shouldFilterPaginateAndSort() {
        var waiting = reserve(BOOK, 1L);
        var cancelled = reserve(31L, 1L);
        cancelled.cancel();
        reservations.save(cancelled);
        reserve(BOOK, 2L);

        assertThat(reservations.findAll(new ReservationFilter(1L, null, null), PageQuery.of(0, 10, "id")).items())
                .extracting(Reservation::getId).containsExactly(waiting.getId(), cancelled.getId());
        assertThat(reservations.findAll(new ReservationFilter(null, BOOK, ReservationStatus.ACTIVE),
                PageQuery.of(0, 1, "id,desc")).items()).hasSize(1);
        assertThat(reservations.findAll(new ReservationFilter(1L, 31L, ReservationStatus.CANCELLED),
                PageQuery.of(0, 10, "reservationDate")).totalItems()).isEqualTo(1);
        assertThat(reservations.findAll(ReservationFilter.none(), PageQuery.of(0, 2, "pickupDeadline"))
                .totalItems()).isEqualTo(3);
    }

    @Test
    void shouldGiveTheQueuePositionsOfSeveralReservationsAtOnce() {
        var first = reserve(BOOK, 1L);
        var second = reserve(BOOK, 2L);
        var holding = reserveAndHold(BOOK, 3L, 11L, DAY);

        assertThat(reservations.queuePositions(java.util.Set.of(first.getId(), second.getId(), holding.getId(), 99L)))
                .containsOnly(java.util.Map.entry(first.getId(), 1L), java.util.Map.entry(second.getId(), 2L));
        assertThat(reservations.queuePositions(java.util.Set.of())).isEmpty();
    }
}
