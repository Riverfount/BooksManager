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
import com.riverfount.booksmanager.catalog.application.port.in.ReserveCopyUseCase;
import com.riverfount.booksmanager.loan.domain.Reservation;
import com.riverfount.booksmanager.shared.application.port.out.Notification;
import com.riverfount.booksmanager.shared.application.port.out.NotificationSender;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.user.application.port.in.FindReaderContactsUseCase;
import com.riverfount.booksmanager.user.application.port.in.ReaderContact;
import org.mockito.ArgumentCaptor;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Passing the turn of a freed copy to the first of the queue (RF18, RN13). */
class ReservationQueueTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final long BOOK = 30L;
    private static final long COPY = 7L;

    private InMemoryReservationRepository reservations;
    private FindCopyAvailabilityUseCase copyAvailability;
    private FindCopySummariesUseCase copySummaries;
    private ReserveCopyUseCase reserveCopy;
    private FindReaderContactsUseCase readerContacts;
    private NotificationSender notificationSender;
    private ReservationQueue queue;

    @BeforeEach
    void setUp() {
        reservations = new InMemoryReservationRepository();
        copyAvailability = mock(FindCopyAvailabilityUseCase.class);
        copySummaries = mock(FindCopySummariesUseCase.class);
        reserveCopy = mock(ReserveCopyUseCase.class);
        readerContacts = mock(FindReaderContactsUseCase.class);
        notificationSender = mock(NotificationSender.class);
        queue = new ReservationQueue(reservations, copyAvailability, copySummaries, reserveCopy, readerContacts,
                notificationSender);
        given(readerContacts.findContacts(Set.of(1L)))
                .willReturn(Map.of(1L, new ReaderContact("Maria Silva", "maria@example.com")));
        copyIsLendable(true);
        given(copySummaries.findSummaries(Set.of(COPY)))
                .willReturn(Map.of(COPY, new CopySummary(COPY, "PAT-7", BOOK, "Dom Casmurro")));
    }

    private void copyIsLendable(boolean lendable) {
        given(copyAvailability.findAvailability(COPY)).willReturn(Optional.of(new CopyAvailability(COPY, lendable, false, true)));
    }

    @Test
    void shouldNotKeepACopyOfAnInactiveBookForAReservationThatCouldNotPickItUp() {
        var waiting = reserve(BOOK, 1L);
        given(copyAvailability.findAvailability(COPY))
                .willReturn(Optional.of(new CopyAvailability(COPY, true, false, false)));

        assertThat(queue.passTheTurn(COPY, TODAY, 3)).isEmpty();

        then(reserveCopy).should(never()).reserve(COPY);
        assertThat(reservations.findById(waiting.getId()).orElseThrow().isWaiting()).isTrue();
    }

    private Reservation reserve(long book, long reader) {
        return reservations.save(Reservation.create(book, reader, TODAY.minusDays(2)));
    }

    @Test
    void shouldGiveTheCopyToTheFirstOfTheQueueWithTheDeadlineOfTheConfiguration() {
        var first = reserve(BOOK, 1L);
        var second = reserve(BOOK, 2L);

        var served = queue.passTheTurn(COPY, TODAY, 3).orElseThrow();

        assertThat(served.getId()).isEqualTo(first.getId());
        assertThat(served.getCopyId()).isEqualTo(COPY);
        assertThat(served.getPickupDeadline()).isEqualTo(TODAY.plusDays(3));
        then(reserveCopy).should().reserve(COPY);
        var stored = reservations.findById(first.getId()).orElseThrow();
        assertThat(stored.isHoldingCopy()).isTrue();
        assertThat(reservations.findById(second.getId()).orElseThrow().isWaiting()).isTrue();
        assertThat(reservations.queuePosition(second.getId())).contains(1L);
    }

    @Test
    void twoCopiesOfTheSameBookShouldGoToDifferentReadersOfTheQueue() {
        var first = reserve(BOOK, 1L);
        var second = reserve(BOOK, 2L);
        given(copyAvailability.findAvailability(8L)).willReturn(Optional.of(new CopyAvailability(8L, true, false, true)));
        given(copySummaries.findSummaries(Set.of(8L)))
                .willReturn(Map.of(8L, new CopySummary(8L, "PAT-8", BOOK, "T")));

        var servedFirst = queue.passTheTurn(COPY, TODAY, 3).orElseThrow();
        var servedSecond = queue.passTheTurn(8L, TODAY, 3).orElseThrow();

        assertThat(servedFirst.getId()).isEqualTo(first.getId());
        assertThat(servedSecond.getId()).isEqualTo(second.getId());
        assertThat(servedSecond.getCopyId()).isEqualTo(8L);
    }

    @Test
    void shouldLeaveTheCopyAloneWhenNobodyWaitsForTheBook() {
        reserve(31L, 1L);

        assertThat(queue.passTheTurn(COPY, TODAY, 3)).isEmpty();

        then(reserveCopy).should(never()).reserve(anyLong());
    }

    @Test
    void aReservationThatAlreadyHoldsACopyIsNotInTheQueue() {
        var holding = Reservation.create(BOOK, 1L, TODAY);
        holding.assignCopy(9L, TODAY, 3);
        reservations.save(holding);

        assertThat(queue.passTheTurn(COPY, TODAY, 3)).isEmpty();
    }

    @Test
    void shouldNotKeepACopyThatCannotBeLent() {
        reserve(BOOK, 1L);
        copyIsLendable(false);

        assertThat(queue.passTheTurn(COPY, TODAY, 3)).isEmpty();

        then(reserveCopy).should(never()).reserve(anyLong());
    }

    @Test
    void shouldDoNothingForACopyTheCatalogDoesNotKnow() {
        reserve(BOOK, 1L);

        assertThat(queue.passTheTurn(99L, TODAY, 3)).isEmpty();
        given(copyAvailability.findAvailability(98L)).willReturn(Optional.of(new CopyAvailability(98L, true, false, true)));
        assertThat(queue.passTheTurn(98L, TODAY, 3)).isEmpty();
    }

    @Test
    void shouldLeaveTheReservationWaitingWhenTheCatalogRefusesToKeepTheCopy() {
        var first = reserve(BOOK, 1L);
        doThrow(new BusinessRuleException("o exemplar não está disponível")).when(reserveCopy).reserve(COPY);

        assertThatThrownBy(() -> queue.passTheTurn(COPY, TODAY, 3)).isInstanceOf(BusinessRuleException.class);

        assertThat(reservations.findById(first.getId()).orElseThrow().isWaiting()).isTrue();
    }

    // the notice to the reader (RF28, RN13, RNF19)

    @Test
    void shouldTellTheFirstOfTheQueueTheBookAndTheLastDayToPickItUp() {
        reserve(BOOK, 1L);
        reserve(BOOK, 2L);

        queue.passTheTurn(COPY, TODAY, 3);

        var sent = ArgumentCaptor.forClass(Notification.class);
        then(notificationSender).should().send(sent.capture());
        var notice = sent.getValue();
        assertThat(notice.recipient()).isEqualTo("maria@example.com");
        assertThat(notice.subject()).isEqualTo("Livro reservado disponível: \"Dom Casmurro\"");
        assertThat(notice.text()).contains("Maria Silva").contains("Dom Casmurro")
                .contains("até 13/11/2026").contains("inclusive");
        then(notificationSender).shouldHaveNoMoreInteractions();
    }

    @Test
    void shouldSendTheNoticeThroughTheSendThatWaitsForTheCommit() {
        reserve(BOOK, 1L);

        queue.passTheTurn(COPY, TODAY, 3);

        then(notificationSender).should().send(org.mockito.ArgumentMatchers.any());
        then(notificationSender).should(never()).sendAndConfirm(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldTellNobodyWhenNobodyWaitsOrTheCopyCannotBeKept() {
        reserve(31L, 1L);
        queue.passTheTurn(COPY, TODAY, 3);

        reserve(BOOK, 1L);
        copyIsLendable(false);
        queue.passTheTurn(COPY, TODAY, 3);

        then(notificationSender).shouldHaveNoInteractions();
    }

    @Test
    void shouldTellNobodyWhenTheCatalogRefusesToKeepTheCopy() {
        reserve(BOOK, 1L);
        doThrow(new BusinessRuleException("o exemplar não está disponível")).when(reserveCopy).reserve(COPY);

        assertThatThrownBy(() -> queue.passTheTurn(COPY, TODAY, 3)).isInstanceOf(BusinessRuleException.class);

        then(notificationSender).shouldHaveNoInteractions();
    }

    @Test
    void shouldStillServeTheReservationWhenTheReaderHasNoContact() {
        reserve(BOOK, 5L);

        var served = queue.passTheTurn(COPY, TODAY, 3);

        assertThat(served).isPresent();
        then(notificationSender).shouldHaveNoInteractions();
    }
}
