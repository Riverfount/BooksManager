package com.riverfount.booksmanager.loan.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.riverfount.booksmanager.catalog.application.port.in.CopySummary;
import com.riverfount.booksmanager.catalog.application.port.in.FindBookTitlesUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.FindCopySummariesUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ListReservationsQuery;
import com.riverfount.booksmanager.loan.application.port.in.ReservationDetails;
import com.riverfount.booksmanager.loan.domain.Reservation;
import com.riverfount.booksmanager.loan.domain.ReservationStatus;
import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.user.application.port.in.FindReaderNamesUseCase;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Querying reservations (RF16): active and closed, with the queue place, always paginated (RNF14). */
class ReservationQueryServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 1);
    private static final long BOOK = 30L;
    private static final long OTHER_BOOK = 31L;

    private InMemoryReservationRepository reservationRepository;
    private FindReaderNamesUseCase readerNames;
    private FindBookTitlesUseCase bookTitles;
    private FindCopySummariesUseCase copySummaries;
    private ReservationQueryService service;

    @BeforeEach
    void setUp() {
        reservationRepository = new InMemoryReservationRepository();
        readerNames = mock(FindReaderNamesUseCase.class);
        bookTitles = mock(FindBookTitlesUseCase.class);
        copySummaries = mock(FindCopySummariesUseCase.class);
        given(readerNames.findNames(anySet())).willReturn(Map.of(1L, "Ana", 2L, "Bia", 3L, "Caio"));
        given(bookTitles.findTitles(anySet())).willReturn(Map.of(BOOK, "Dom Casmurro", OTHER_BOOK, "Quincas Borba"));
        given(copySummaries.findSummaries(anySet()))
                .willReturn(Map.of(10L, new CopySummary(10L, "PAT-10", BOOK, "Dom Casmurro")));
        service = new ReservationQueryService(reservationRepository, readerNames, bookTitles, copySummaries);
    }

    private Reservation reserve(long book, long reader) {
        return reservationRepository.save(Reservation.create(book, reader, DAY));
    }

    private Reservation reserveAndHold(long book, long reader, long copy) {
        var reservation = Reservation.create(book, reader, DAY);
        reservation.assignCopy(copy, DAY, 3);
        return reservationRepository.save(reservation);
    }

    private Reservation close(Reservation reservation) {
        reservation.cancel();
        return reservationRepository.save(reservation);
    }

    private static ListReservationsQuery query(Long reader, Long book, ReservationStatus status) {
        return new ListReservationsQuery(reader, book, status, PageQuery.of(0, 20, "id"));
    }

    // by id

    @Test
    void shouldFindAWaitingReservationWithItsPlaceInTheQueueAndTheNames() {
        reserve(BOOK, 1L);
        var second = reserve(BOOK, 2L);

        var details = service.findById(second.getId()).orElseThrow();

        assertThat(details.reservation().getId()).isEqualTo(second.getId());
        assertThat(details.queuePosition()).isEqualTo(2L);
        assertThat(details.readerName()).isEqualTo("Bia");
        assertThat(details.bookTitle()).isEqualTo("Dom Casmurro");
        assertThat(details.copy()).isNull();
    }

    @Test
    void shouldFindAReservationThatHoldsACopyWithTheCopyAndNoPlaceInTheQueue() {
        var holding = reserveAndHold(BOOK, 1L, 10L);

        var details = service.findById(holding.getId()).orElseThrow();

        assertThat(details.queuePosition()).isNull();
        assertThat(details.copy().assetCode()).isEqualTo("PAT-10");
        assertThat(details.reservation().getPickupDeadline()).isEqualTo(DAY.plusDays(3));
    }

    @Test
    void aClosedReservationShouldStillBeFoundWithoutAPlaceInTheQueue() {
        var cancelled = close(reserve(BOOK, 1L));

        var details = service.findById(cancelled.getId()).orElseThrow();

        assertThat(details.reservation().getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(details.queuePosition()).isNull();
    }

    @Test
    void shouldFindNothingForAnUnknownReservation() {
        assertThat(service.findById(999L)).isEmpty();
    }

    // listing

    @Test
    void shouldListEveryReservationWhenThereIsNoFilterClosedOnesIncluded() {
        reserve(BOOK, 1L);
        close(reserve(BOOK, 2L));
        reserveAndHold(OTHER_BOOK, 3L, 10L);

        var page = service.list(query(null, null, null));

        assertThat(page.totalItems()).isEqualTo(3);
        assertThat(page.items()).extracting(details -> details.reservation().getReaderId())
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void shouldFilterByReaderByBookAndByStatus() {
        reserve(BOOK, 1L);
        close(reserve(OTHER_BOOK, 1L));
        reserve(BOOK, 2L);

        assertThat(service.list(query(1L, null, null)).totalItems()).isEqualTo(2);
        assertThat(service.list(query(null, BOOK, null)).totalItems()).isEqualTo(2);
        assertThat(service.list(query(null, null, ReservationStatus.CANCELLED)).items())
                .extracting(details -> details.reservation().getBookId()).containsExactly(OTHER_BOOK);
        assertThat(service.list(query(1L, BOOK, ReservationStatus.ACTIVE)).totalItems()).isEqualTo(1);
    }

    @Test
    void thePositionShouldFollowTheOrderOfArrivalWhateverTheSortOfThePage() {
        var first = reserve(BOOK, 1L);
        var second = reserve(BOOK, 2L);
        var third = reserve(BOOK, 3L);
        reserve(OTHER_BOOK, 1L);

        var page = service.list(new ListReservationsQuery(null, BOOK, ReservationStatus.ACTIVE,
                PageQuery.of(0, 20, "id,desc")));

        assertThat(page.items()).extracting(details -> details.reservation().getId())
                .containsExactly(third.getId(), second.getId(), first.getId());
        assertThat(page.items()).extracting(ReservationDetails::queuePosition).containsExactly(3L, 2L, 1L);
    }

    @Test
    void shouldPaginateAndKeepThePositionOfTheReservationsOutsideThePage() {
        reserve(BOOK, 1L);
        var second = reserve(BOOK, 2L);
        reserve(BOOK, 3L);

        var page = service.list(new ListReservationsQuery(null, null, null, PageQuery.of(1, 1, "id")));

        assertThat(page.totalItems()).isEqualTo(3);
        assertThat(page.items()).singleElement().satisfies(details -> {
            assertThat(details.reservation().getId()).isEqualTo(second.getId());
            assertThat(details.queuePosition()).isEqualTo(2L);
        });
    }

    @Test
    void shouldFetchTheNamesTitlesAndCopiesOfTheWholePageInOneCallEach() {
        reserve(BOOK, 1L);
        reserve(OTHER_BOOK, 2L);
        reserveAndHold(BOOK, 3L, 10L);

        var page = service.list(query(null, null, null));

        assertThat(page.items()).hasSize(3);
        then(readerNames).should(times(1)).findNames(Set.of(1L, 2L, 3L));
        then(bookTitles).should(times(1)).findTitles(Set.of(BOOK, OTHER_BOOK));
        then(copySummaries).should(times(1)).findSummaries(Set.of(10L));
    }

    @Test
    void shouldReturnAnEmptyPageWithoutAskingOtherModulesAnything() {
        var page = service.list(query(null, null, null));

        assertThat(page.items()).isEmpty();
        assertThat(page.totalItems()).isZero();
        then(readerNames).should(never()).findNames(anySet());
        then(bookTitles).should(never()).findTitles(anySet());
        then(copySummaries).should(never()).findSummaries(anySet());
    }

    @Test
    void shouldRefuseToSortByAFieldThatIsNotListed() {
        assertThatThrownBy(() -> new ListReservationsQuery(null, null, null, PageQuery.of(0, 20, "readerName")))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessageContaining("id, pickupDeadline, reservationDate");
    }
}
