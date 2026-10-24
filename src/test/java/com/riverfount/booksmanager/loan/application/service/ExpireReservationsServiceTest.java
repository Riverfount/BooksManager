package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.riverfount.booksmanager.catalog.application.port.in.CopyAvailability;
import com.riverfount.booksmanager.catalog.application.port.in.CopySummary;
import com.riverfount.booksmanager.catalog.application.port.in.FindCopyAvailabilityUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.FindCopySummariesUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ReleaseCopyReservationUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ReserveCopyUseCase;
import com.riverfount.booksmanager.loan.domain.Reservation;
import com.riverfount.booksmanager.loan.domain.ReservationStatus;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.application.port.out.NotificationSender;
import com.riverfount.booksmanager.user.application.port.in.FindReaderContactsUseCase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Expiring the reservations past their pickup deadline (RF19, RN13): the service only orchestrates; the deadline
 * is inclusive (the last day is still in time), the freed copy goes to the next of the queue with a deadline that
 * starts today, and running it again changes nothing.
 */
class ExpireReservationsServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final long BOOK = 30L;

    private InMemoryLibraryConfigurationRepository configurationRepository;
    private InMemoryReservationRepository reservationRepository;
    private ReleaseCopyReservationUseCase releaseCopyReservation;
    private ReserveCopyUseCase reserveCopy;
    private FindCopyAvailabilityUseCase copyAvailability;
    private FindCopySummariesUseCase copySummaries;
    private ExpireReservationsService service;
    private ReservationExpirer expirer;

    @BeforeEach
    void setUp() {
        configurationRepository = new InMemoryLibraryConfigurationRepository();
        reservationRepository = new InMemoryReservationRepository();
        releaseCopyReservation = mock(ReleaseCopyReservationUseCase.class);
        reserveCopy = mock(ReserveCopyUseCase.class);
        copyAvailability = mock(FindCopyAvailabilityUseCase.class);
        copySummaries = mock(FindCopySummariesUseCase.class);
        var queue = new ReservationQueue(reservationRepository, copyAvailability, copySummaries, reserveCopy, mock(FindReaderContactsUseCase.class), mock(NotificationSender.class));
        expirer = new ReservationExpirer(configurationRepository, reservationRepository, releaseCopyReservation,
                queue);
        service = new ExpireReservationsService(reservationRepository, expirer, CLOCK);
    }

    /** Once released, the copy is on the shelf of the book and can be lent. */
    private void copyIsOnTheShelf(long copy, long book) {
        given(copyAvailability.findAvailability(copy)).willReturn(Optional.of(new CopyAvailability(copy, true, false, true)));
        given(copySummaries.findSummaries(Set.of(copy)))
                .willReturn(Map.of(copy, new CopySummary(copy, "PAT-" + copy, book, "T")));
    }

    /** A reservation holding the copy until {@code deadline} (inclusive), the way the return leaves it. */
    private Reservation holding(long book, long reader, long copy, LocalDate deadline) {
        copyIsOnTheShelf(copy, book);
        var reservation = Reservation.create(book, reader, deadline.minusDays(10));
        reservation.assignCopy(copy, deadline.minusDays(3), 3);
        return reservationRepository.save(reservation);
    }

    private Reservation waiting(long book, long reader) {
        return reservationRepository.save(Reservation.create(book, reader, TODAY.minusDays(8)));
    }

    private Reservation stored(Reservation reservation) {
        return reservationRepository.findById(reservation.getId()).orElseThrow();
    }

    // the deadline is inclusive (RN13)

    @Test
    void shouldNotExpireOnTheLastDayOfTheDeadlineNorBeforeIt() {
        var before = holding(BOOK, 1L, 7L, TODAY.plusDays(1));
        var lastDay = holding(31L, 2L, 8L, TODAY);

        assertThat(service.expireOverdue()).isZero();

        assertThat(stored(before).isHoldingCopy()).isTrue();
        assertThat(stored(lastDay).isHoldingCopy()).isTrue();
        then(releaseCopyReservation).should(never()).releaseReservation(anyLong());
    }

    @Test
    void shouldExpireTheDayAfterTheDeadline() {
        var late = holding(BOOK, 1L, 7L, TODAY.minusDays(1));

        assertThat(service.expireOverdue()).isEqualTo(1);

        assertThat(stored(late).getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        then(releaseCopyReservation).should().releaseReservation(7L);
    }

    @Test
    void aReservationThatOnlyWaitsInTheQueueNeverExpires() {
        var waiting = waiting(BOOK, 1L);

        assertThat(service.expireOverdue()).isZero();

        assertThat(stored(waiting).isWaiting()).isTrue();
    }

    // where the copy goes

    @Test
    void shouldPassTheCopyToTheNextOfTheQueueWithADeadlineThatStartsToday() {
        var late = holding(BOOK, 1L, 7L, TODAY.minusDays(1));
        var next = waiting(BOOK, 2L);
        var afterNext = waiting(BOOK, 3L);

        service.expireOverdue();

        then(reserveCopy).should().reserve(7L);
        assertThat(stored(next).getCopyId()).isEqualTo(7L);
        assertThat(stored(next).getPickupDeadline()).isEqualTo(TODAY.plusDays(3));
        assertThat(stored(afterNext).isWaiting()).isTrue();
        assertThat(stored(late).getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    void shouldUseThePickupDaysOfTheConfiguration() {
        var configuration = configurationRepository.getForUpdate();
        configuration.update(null, null, null, null, null, null, null, null, 5, null);
        configurationRepository.save(configuration);
        holding(BOOK, 1L, 7L, TODAY.minusDays(1));
        var next = waiting(BOOK, 2L);

        service.expireOverdue();

        assertThat(stored(next).getPickupDeadline()).isEqualTo(TODAY.plusDays(5));
    }

    @Test
    void shouldLeaveTheCopyOnTheShelfWhenNobodyIsWaiting() {
        holding(BOOK, 1L, 7L, TODAY.minusDays(1));

        assertThat(service.expireOverdue()).isEqualTo(1);

        then(releaseCopyReservation).should().releaseReservation(7L);
        then(reserveCopy).should(never()).reserve(anyLong());
    }

    @Test
    void severalExpiredReservationsOfTheSameBookShouldFeedTheQueueInOrderOfArrival() {
        holding(BOOK, 1L, 7L, TODAY.minusDays(2));
        holding(BOOK, 2L, 8L, TODAY.minusDays(1));
        var third = waiting(BOOK, 3L);
        var fourth = waiting(BOOK, 4L);
        var fifth = waiting(BOOK, 5L);

        assertThat(service.expireOverdue()).isEqualTo(2);

        // the oldest deadline is handled first, and each freed copy goes to the head of the queue
        assertThat(stored(third).getCopyId()).isEqualTo(7L);
        assertThat(stored(fourth).getCopyId()).isEqualTo(8L);
        assertThat(stored(fifth).isWaiting()).isTrue();
        assertThat(reservationRepository.queuePosition(fifth.getId())).contains(1L);
    }

    @Test
    void theReservationThatJustReceivedACopyShouldNotExpireInTheSameRun() {
        holding(BOOK, 1L, 7L, TODAY.minusDays(1));
        var next = waiting(BOOK, 2L);

        assertThat(service.expireOverdue()).isEqualTo(1);

        assertThat(stored(next).isHoldingCopy()).isTrue();
    }

    // idempotence

    @Test
    void runningTwiceInARowShouldChangeNothingTheSecondTime() {
        holding(BOOK, 1L, 7L, TODAY.minusDays(1));
        var next = waiting(BOOK, 2L);

        assertThat(service.expireOverdue()).isEqualTo(1);
        var afterFirst = stored(next);

        assertThat(service.expireOverdue()).isZero();
        assertThat(stored(next).getCopyId()).isEqualTo(afterFirst.getCopyId());
        assertThat(stored(next).getPickupDeadline()).isEqualTo(afterFirst.getPickupDeadline());
        then(releaseCopyReservation).should().releaseReservation(7L); // once, not twice
    }

    @Test
    void shouldDoNothingForAReservationClosedInTheMeantime() {
        var late = holding(BOOK, 1L, 7L, TODAY.minusDays(1));
        late.cancel();
        reservationRepository.save(late);

        assertThat(expirer.expire(late.getId(), TODAY)).isFalse();
        assertThat(expirer.expire(999L, TODAY)).isFalse();
        then(releaseCopyReservation).should(never()).releaseReservation(anyLong());
    }

    // one failure does not hold back the others (RNF16)

    @Test
    void aReservationThatFailsShouldNotStopTheOthers() {
        holding(BOOK, 1L, 7L, TODAY.minusDays(3)); // handled first, and fails
        var fine = holding(31L, 2L, 8L, TODAY.minusDays(1));
        doThrow(new BusinessRuleException("o exemplar não está reservado")).when(releaseCopyReservation)
                .releaseReservation(7L);

        assertThat(service.expireOverdue()).isEqualTo(1);

        assertThat(stored(fine).getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        then(releaseCopyReservation).should().releaseReservation(8L);
    }
}
