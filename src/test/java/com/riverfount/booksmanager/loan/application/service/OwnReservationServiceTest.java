package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.riverfount.booksmanager.catalog.application.port.in.CopySummary;
import com.riverfount.booksmanager.loan.application.port.in.CancelOwnReservationCommand;
import com.riverfount.booksmanager.loan.application.port.in.CancelReservationCommand;
import com.riverfount.booksmanager.loan.application.port.in.CancelReservationResult;
import com.riverfount.booksmanager.loan.application.port.in.CancelReservationUseCase;
import com.riverfount.booksmanager.loan.application.port.in.GetOwnReservationQuery;
import com.riverfount.booksmanager.loan.application.port.in.GetReservationUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ListOwnReservationsQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListReservationsQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListReservationsUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ReservationDetails;
import com.riverfount.booksmanager.loan.application.port.in.ReserveBookCommand;
import com.riverfount.booksmanager.loan.application.port.in.ReserveBookResult;
import com.riverfount.booksmanager.loan.application.port.in.ReserveBookUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ReserveOwnBookCommand;
import com.riverfount.booksmanager.loan.domain.Reservation;
import com.riverfount.booksmanager.loan.domain.ReservationStatus;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * What a reader does with their own reservations (Phase 6, RF16, RF17): the reader of the token is the only reader
 * that is ever acted for, a reservation of another reader is answered exactly like one that does not exist (before
 * any other rule, so its state is not revealed), and nothing of another reader comes back.
 */
class OwnReservationServiceTest {

    private static final long ME = 42L;
    private static final long ANOTHER = 43L;
    private static final long BOOK = 30L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private ReserveBookUseCase reserve;
    private GetReservationUseCase get;
    private ListReservationsUseCase list;
    private CancelReservationUseCase cancel;
    private OwnReservationService service;

    @BeforeEach
    void setUp() {
        reserve = mock(ReserveBookUseCase.class);
        get = mock(GetReservationUseCase.class);
        list = mock(ListReservationsUseCase.class);
        cancel = mock(CancelReservationUseCase.class);
        service = new OwnReservationService(reserve, get, list, cancel);
    }

    private static Reservation reservation(long id, long readerId, ReservationStatus status) {
        return Reservation.reconstitute(id, BOOK, readerId, TODAY, status, null, null);
    }

    private static ReservationDetails details(Reservation reservation, Long queuePosition) {
        return new ReservationDetails(reservation, queuePosition, "Maria Silva", "Dom Casmurro",
                new CopySummary(7L, "PAT-0007", BOOK, "Dom Casmurro"));
    }

    // reserving

    @Test
    void shouldReserveForTheReaderOfTheTokenAndReturnTheirReservation() {
        var created = reservation(5L, ME, ReservationStatus.ACTIVE);
        given(reserve.reserve(new ReserveBookCommand(BOOK, ME))).willReturn(new ReserveBookResult(created, 3L));
        given(get.findById(5L)).willReturn(Optional.of(details(created, 3L)));

        var result = service.reserve(new ReserveOwnBookCommand(BOOK, ME));

        assertThat(result.reservation().getId()).isEqualTo(5L);
        assertThat(result.queuePosition()).isEqualTo(3L);
        assertThat(result.bookTitle()).isEqualTo("Dom Casmurro");
    }

    @Test
    void shouldPassOnTheRefusalOfTheReservationWithEveryReason() {
        given(reserve.reserve(any())).willThrow(new BusinessRuleException("multa pendente; livro com exemplar disponível"));

        assertThatThrownBy(() -> service.reserve(new ReserveOwnBookCommand(BOOK, ME)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("multa pendente")
                .hasMessageContaining("exemplar disponível");
    }

    // looking at one

    @Test
    void shouldShowAReservationOfTheReader() {
        given(get.findById(5L)).willReturn(Optional.of(details(reservation(5L, ME, ReservationStatus.ACTIVE), 2L)));

        var found = service.get(new GetOwnReservationQuery(5L, ME));

        assertThat(found.queuePosition()).isEqualTo(2L);
    }

    @Test
    void aReservationOfAnotherReaderShouldBeAsNotFoundAsOneThatDoesNotExist() {
        given(get.findById(5L)).willReturn(Optional.of(details(reservation(5L, ANOTHER, ReservationStatus.ACTIVE), 1L)));
        given(get.findById(6L)).willReturn(Optional.empty());

        var another = catchMessage(() -> service.get(new GetOwnReservationQuery(5L, ME)));
        var missing = catchMessage(() -> service.get(new GetOwnReservationQuery(6L, ME)));

        assertThat(another.replaceAll("\\d+", "N")).isEqualTo(missing.replaceAll("\\d+", "N"));
    }

    // listing

    @Test
    void shouldListOnlyTheReservationsOfTheReaderWhateverTheFilters() {
        var page = PageQuery.of(0, 20, "id,desc");
        given(list.list(any())).willReturn(new PageResult<>(List.of(), 0, 20, 0));

        service.list(new ListOwnReservationsQuery(ME, ReservationStatus.ACTIVE, page));

        var captor = ArgumentCaptor.forClass(ListReservationsQuery.class);
        then(list).should().list(captor.capture());
        assertThat(captor.getValue().readerId()).isEqualTo(ME);
        assertThat(captor.getValue().bookId()).isNull();
        assertThat(captor.getValue().status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(captor.getValue().page()).isEqualTo(page);
    }

    // cancelling

    @Test
    void shouldCancelAReservationOfTheReaderAndReturnOnlyTheirs() {
        var mine = reservation(5L, ME, ReservationStatus.ACTIVE);
        var cancelled = reservation(5L, ME, ReservationStatus.CANCELLED);
        var servedToAnother = reservation(9L, ANOTHER, ReservationStatus.ACTIVE);
        given(get.findById(5L)).willReturn(Optional.of(details(mine, 1L)), Optional.of(details(cancelled, null)));
        given(cancel.cancel(new CancelReservationCommand(5L)))
                .willReturn(new CancelReservationResult(cancelled, servedToAnother));

        var result = service.cancel(new CancelOwnReservationCommand(5L, ME));

        assertThat(result.reservation().getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(result.reservation().getReaderId()).isEqualTo(ME);
        // the reservation that got the freed copy belongs to someone else: it is not part of the answer
        assertThat(result.toString()).doesNotContain("reservation=Reservation[id=9");
        assertThat(result.reservation().getId()).isNotEqualTo(9L);
    }

    @Test
    void shouldNeverCancelAReservationOfAnotherReaderNorSayItExists() {
        given(get.findById(5L)).willReturn(Optional.of(details(reservation(5L, ANOTHER, ReservationStatus.ACTIVE), 1L)));
        given(get.findById(6L)).willReturn(Optional.empty());

        var another = catchMessage(() -> service.cancel(new CancelOwnReservationCommand(5L, ME)));
        var missing = catchMessage(() -> service.cancel(new CancelOwnReservationCommand(6L, ME)));

        assertThat(another.replaceAll("\\d+", "N")).isEqualTo(missing.replaceAll("\\d+", "N"));
        then(cancel).should(never()).cancel(any());
    }

    @Test
    void aClosedReservationOfAnotherReaderIsStillNotFoundNotAnUnprocessableOne() {
        // otherwise the refusal ("already cancelled") would tell the state of somebody else's reservation
        given(get.findById(5L)).willReturn(Optional.of(details(reservation(5L, ANOTHER, ReservationStatus.CANCELLED), null)));

        assertThatThrownBy(() -> service.cancel(new CancelOwnReservationCommand(5L, ME)))
                .isInstanceOf(ResourceNotFoundException.class);
        then(cancel).should(never()).cancel(any());
    }

    @Test
    void aClosedReservationOfTheReaderIsRefusedByTheRuleOfTheCancellation() {
        given(get.findById(5L)).willReturn(Optional.of(details(reservation(5L, ME, ReservationStatus.CANCELLED), null)));
        given(cancel.cancel(any())).willThrow(new BusinessRuleException("a reserva já está encerrada"));

        assertThatThrownBy(() -> service.cancel(new CancelOwnReservationCommand(5L, ME)))
                .isInstanceOf(BusinessRuleException.class);
    }

    private static String catchMessage(Runnable action) {
        try {
            action.run();
        } catch (ResourceNotFoundException e) {
            return e.getMessage();
        }
        throw new AssertionError("expected a ResourceNotFoundException");
    }
}
