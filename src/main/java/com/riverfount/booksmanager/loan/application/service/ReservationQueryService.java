package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.FindBookTitlesUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.FindCopySummariesUseCase;
import com.riverfount.booksmanager.loan.application.port.in.GetReservationUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ListReservationsQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListReservationsUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ReservationDetails;
import com.riverfount.booksmanager.loan.application.port.out.ReservationFilter;
import com.riverfount.booksmanager.loan.application.port.out.ReservationRepository;
import com.riverfount.booksmanager.loan.domain.Reservation;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.user.application.port.in.FindReaderNamesUseCase;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Queries on reservations (RF16, RNF14): finds one or lists a page, active or closed, each with the place in
 * the queue (while it waits), the name of its reader, the title of its book and the copy it holds. The queue
 * places, the readers' names, the titles and the copies' summaries of a whole page are fetched in one call
 * each, not one per reservation. Nothing here decides anything: it only reads.
 */
@Service
@Transactional(readOnly = true)
class ReservationQueryService implements GetReservationUseCase, ListReservationsUseCase {

    private final ReservationRepository reservationRepository;
    private final FindReaderNamesUseCase readerNames;
    private final FindBookTitlesUseCase bookTitles;
    private final FindCopySummariesUseCase copySummaries;

    ReservationQueryService(ReservationRepository reservationRepository, FindReaderNamesUseCase readerNames,
            FindBookTitlesUseCase bookTitles, FindCopySummariesUseCase copySummaries) {
        this.reservationRepository = reservationRepository;
        this.readerNames = readerNames;
        this.bookTitles = bookTitles;
        this.copySummaries = copySummaries;
    }

    @Override
    public Optional<ReservationDetails> findById(Long id) {
        return reservationRepository.findById(id).map(reservation -> withDetails(List.of(reservation)).get(0));
    }

    @Override
    public PageResult<ReservationDetails> list(ListReservationsQuery query) {
        var filter = new ReservationFilter(query.readerId(), query.bookId(), query.status());
        var page = reservationRepository.findAll(filter, query.page());
        return new PageResult<>(withDetails(page.items()), page.page(), page.size(), page.totalItems());
    }

    private List<ReservationDetails> withDetails(List<Reservation> reservations) {
        if (reservations.isEmpty()) {
            return List.of();
        }
        var positions = reservationRepository.queuePositions(ids(reservations, Reservation::getId));
        var names = readerNames.findNames(ids(reservations, Reservation::getReaderId));
        var titles = bookTitles.findTitles(ids(reservations, Reservation::getBookId));
        var copies = copySummaries.findSummaries(reservations.stream().map(Reservation::getCopyId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        return reservations.stream()
                .map(reservation -> new ReservationDetails(reservation, positions.get(reservation.getId()),
                        names.get(reservation.getReaderId()), titles.get(reservation.getBookId()),
                        reservation.getCopyId() == null ? null : copies.get(reservation.getCopyId())))
                .toList();
    }

    private static Set<Long> ids(List<Reservation> reservations,
            Function<Reservation, Long> idOf) {
        return reservations.stream().map(idOf).collect(Collectors.toSet());
    }
}
