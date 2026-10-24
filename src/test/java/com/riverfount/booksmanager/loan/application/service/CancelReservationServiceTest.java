package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.riverfount.booksmanager.loan.application.port.in.CancelReservationCommand;
import com.riverfount.booksmanager.loan.domain.Reservation;
import com.riverfount.booksmanager.loan.domain.ReservationStatus;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
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
 * Cancelling a reservation (RF17, RF18): the service only orchestrates; closing the reservation is the
 * domain's, and a copy it held goes to the next of the queue or back to the shelf (the pickup days come from the
 * configuration, RN16).
 */
class CancelReservationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final long BOOK = 30L;
    private static final long COPY = 7L;

    private InMemoryLibraryConfigurationRepository configurationRepository;
    private InMemoryReservationRepository reservationRepository;
    private ReleaseCopyReservationUseCase releaseCopyReservation;
    private ReserveCopyUseCase reserveCopy;
    private CancelReservationService service;

    @BeforeEach
    void setUp() {
        configurationRepository = new InMemoryLibraryConfigurationRepository();
        reservationRepository = new InMemoryReservationRepository();
        releaseCopyReservation = mock(ReleaseCopyReservationUseCase.class);
        reserveCopy = mock(ReserveCopyUseCase.class);
        var copyAvailability = mock(FindCopyAvailabilityUseCase.class);
        var copySummaries = mock(FindCopySummariesUseCase.class);
        // once released, the copy is on the shelf of its book and can be lent
        given(copyAvailability.findAvailability(COPY)).willReturn(Optional.of(new CopyAvailability(COPY, true, false, true)));
        given(copySummaries.findSummaries(Set.of(COPY)))
                .willReturn(Map.of(COPY, new CopySummary(COPY, "PAT-7", BOOK, "T")));
        var queue = new ReservationQueue(reservationRepository, copyAvailability, copySummaries, reserveCopy, mock(FindReaderContactsUseCase.class), mock(NotificationSender.class));
        service = new CancelReservationService(configurationRepository, reservationRepository,
                releaseCopyReservation, queue, CLOCK);
    }

    private Reservation waiting(long reader) {
        return reservationRepository.save(Reservation.create(BOOK, reader, TODAY.minusDays(3)));
    }

    private Reservation holdingTheCopy(long reader) {
        var reservation = Reservation.create(BOOK, reader, TODAY.minusDays(5));
        reservation.assignCopy(COPY, TODAY.minusDays(1), 3);
        return reservationRepository.save(reservation);
    }

    private Reservation stored(Reservation reservation) {
        return reservationRepository.findById(reservation.getId()).orElseThrow();
    }

    // a reservation waiting in the queue

    @Test
    void shouldCancelAReservationThatWaitsInTheQueueWithoutTouchingAnyCopy() {
        var first = waiting(1L);
        var second = waiting(2L);

        var result = service.cancel(new CancelReservationCommand(first.getId()));

        assertThat(result.reservation().getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(result.served()).isNull();
        assertThat(stored(first).getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        then(releaseCopyReservation).should(never()).releaseReservation(anyLong());
        then(reserveCopy).should(never()).reserve(anyLong());
        assertThat(reservationRepository.queuePosition(second.getId())).contains(1L);
    }

    // a reservation that holds a copy

    @Test
    void shouldPassTheHeldCopyOnToTheNextOfTheQueue() {
        var holding = holdingTheCopy(1L);
        var next = waiting(2L);
        var afterNext = waiting(3L);

        var result = service.cancel(new CancelReservationCommand(holding.getId()));

        assertThat(result.reservation().getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(result.served().getId()).isEqualTo(next.getId());
        then(releaseCopyReservation).should().releaseReservation(COPY);
        then(reserveCopy).should().reserve(COPY);
        assertThat(stored(next).getCopyId()).isEqualTo(COPY);
        assertThat(stored(next).getPickupDeadline()).isEqualTo(TODAY.plusDays(3));
        assertThat(stored(afterNext).isWaiting()).isTrue();
        assertThat(stored(holding).getCopyId()).isEqualTo(COPY); // the cancelled one keeps its history
    }

    @Test
    void shouldTakeThePickupDaysFromTheConfiguration() {
        var configuration = configurationRepository.getForUpdate();
        configuration.update(null, null, null, null, null, null, null, null, 5, null);
        configurationRepository.save(configuration);
        var holding = holdingTheCopy(1L);
        var next = waiting(2L);

        service.cancel(new CancelReservationCommand(holding.getId()));

        assertThat(stored(next).getPickupDeadline()).isEqualTo(TODAY.plusDays(5));
    }

    @Test
    void shouldLeaveTheCopyOnTheShelfWhenNobodyIsWaiting() {
        var holding = holdingTheCopy(1L);

        var result = service.cancel(new CancelReservationCommand(holding.getId()));

        assertThat(result.served()).isNull();
        then(releaseCopyReservation).should().releaseReservation(COPY);
        then(reserveCopy).should(never()).reserve(anyLong());
    }

    @Test
    void aWaitingReservationOfAnotherBookShouldNotGetTheCopy() {
        var holding = holdingTheCopy(1L);
        reservationRepository.save(Reservation.create(31L, 2L, TODAY.minusDays(3)));

        assertThat(service.cancel(new CancelReservationCommand(holding.getId())).served()).isNull();
    }

    // refusals

    @Test
    void shouldRefuseAReservationThatIsAlreadyClosed() {
        var cancelled = waiting(1L);
        cancelled.cancel();
        reservationRepository.save(cancelled);
        var expired = holdingTheCopy(2L);
        expired.expire(TODAY.plusDays(10));
        reservationRepository.save(expired);

        for (var closed : new Reservation[] {cancelled, expired}) {
            assertThatThrownBy(() -> service.cancel(new CancelReservationCommand(closed.getId())))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("já foi encerrada");
        }
        then(releaseCopyReservation).should(never()).releaseReservation(anyLong());
    }

    @Test
    void shouldFailWithNotFoundForAReservationThatDoesNotExist() {
        assertThatThrownBy(() -> service.cancel(new CancelReservationCommand(999L)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("999");
    }

    @Test
    void shouldFailTheCancellationWhenTheCatalogRefusesToReleaseTheCopy() {
        var holding = holdingTheCopy(1L);
        doThrow(new BusinessRuleException("o exemplar não está reservado")).when(releaseCopyReservation)
                .releaseReservation(COPY);

        assertThatThrownBy(() -> service.cancel(new CancelReservationCommand(holding.getId())))
                .isInstanceOf(BusinessRuleException.class);
    }
}
