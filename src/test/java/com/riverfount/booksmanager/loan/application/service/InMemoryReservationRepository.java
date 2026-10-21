package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.loan.application.port.out.ReservationFilter;
import com.riverfount.booksmanager.loan.application.port.out.ReservationRepository;
import com.riverfount.booksmanager.loan.domain.Reservation;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * In-memory implementation of ReservationRepository, used only in the application services'
 * unit tests (no Spring, no database). Like the database indexes, it refuses a second active
 * reservation of the same book by the same reader (RN12) and a second active one holding the same
 * copy. The queue is in order of arrival, which is the order of the ids. Does not need to be thread-safe.
 */
class InMemoryReservationRepository implements ReservationRepository {

    private static final Map<String, Comparator<Reservation>> COMPARATORS = Map.of(
            "id", Comparator.comparing(Reservation::getId),
            "reservationDate", Comparator.comparing(Reservation::getReservationDate),
            "pickupDeadline", Comparator.comparing(Reservation::getPickupDeadline,
                    Comparator.nullsLast(Comparator.<LocalDate>naturalOrder())));

    private final Map<Long, Reservation> reservations = new HashMap<>();
    private long nextId = 1;

    /** Like the JPA adapter, the repository never hands out the stored instance. */
    private static Reservation copyWithId(Reservation reservation, Long id) {
        return Reservation.reconstitute(id, reservation.getBookId(), reservation.getReaderId(),
                reservation.getReservationDate(), reservation.getStatus(), reservation.getCopyId(),
                reservation.getPickupDeadline());
    }

    @Override
    public Reservation save(Reservation reservation) {
        if (reservation.isActive()) {
            var others = reservations.values().stream()
                    .filter(stored -> stored.isActive() && !stored.getId().equals(reservation.getId())).toList();
            if (others.stream().anyMatch(stored -> stored.getReaderId().equals(reservation.getReaderId())
                    && stored.getBookId().equals(reservation.getBookId()))) {
                throw new BusinessRuleException("o leitor já tem uma reserva ativa deste livro");
            }
            if (reservation.getCopyId() != null && others.stream()
                    .anyMatch(stored -> reservation.getCopyId().equals(stored.getCopyId()))) {
                throw new BusinessRuleException("o exemplar já está guardado para outra reserva ativa");
            }
        }
        var id = reservation.getId() != null ? reservation.getId() : nextId++;
        reservations.put(id, copyWithId(reservation, id));
        return copyWithId(reservation, id);
    }

    @Override
    public Optional<Reservation> findById(Long id) {
        return Optional.ofNullable(reservations.get(id)).map(reservation -> copyWithId(reservation, id));
    }

    @Override
    public PageResult<Reservation> findAll(ReservationFilter filter, PageQuery page) {
        var result = InMemoryPages.of(reservations.values(),
                reservation -> (filter.readerId() == null || reservation.getReaderId().equals(filter.readerId()))
                        && (filter.bookId() == null || reservation.getBookId().equals(filter.bookId()))
                        && (filter.status() == null || reservation.getStatus() == filter.status()),
                COMPARATORS, Reservation::getId, page);
        return new PageResult<>(
                result.items().stream().map(reservation -> copyWithId(reservation, reservation.getId())).toList(),
                result.page(), result.size(), result.totalItems());
    }

    @Override
    public Optional<Reservation> findFirstWaiting(Long bookId) {
        return waitingOf(bookId).stream().findFirst();
    }

    @Override
    public Optional<Long> queuePosition(Long reservationId) {
        var stored = reservations.get(reservationId);
        if (stored == null) {
            return Optional.empty();
        }
        var waiting = waitingOf(stored.getBookId());
        for (var position = 0; position < waiting.size(); position++) {
            if (waiting.get(position).getId().equals(reservationId)) {
                return Optional.of(position + 1L);
            }
        }
        return Optional.empty();
    }

    @Override
    public Map<Long, Long> queuePositions(Set<Long> reservationIds) {
        var positions = new HashMap<Long, Long>();
        reservationIds.forEach(id -> queuePosition(id).ifPresent(position -> positions.put(id, position)));
        return positions;
    }

    @Override
    public long countActiveByReader(Long readerId) {
        return reservations.values().stream()
                .filter(reservation -> reservation.isActive() && reservation.getReaderId().equals(readerId)).count();
    }

    @Override
    public List<Reservation> findPastDeadline(LocalDate today) {
        return reservations.values().stream().filter(reservation -> reservation.isPastDeadline(today))
                .sorted(Comparator.comparing(Reservation::getPickupDeadline).thenComparing(Reservation::getId))
                .map(reservation -> copyWithId(reservation, reservation.getId())).toList();
    }

    private List<Reservation> waitingOf(Long bookId) {
        return reservations.values().stream()
                .filter(reservation -> reservation.isWaiting() && reservation.getBookId().equals(bookId))
                .sorted(Comparator.comparing(Reservation::getId))
                .map(reservation -> copyWithId(reservation, reservation.getId())).toList();
    }
}
