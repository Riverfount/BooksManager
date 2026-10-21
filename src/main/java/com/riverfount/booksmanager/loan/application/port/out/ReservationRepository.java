package com.riverfount.booksmanager.loan.application.port.out;

import com.riverfount.booksmanager.loan.domain.Reservation;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Outbound port for reservations (RF16, RF17, RF19), in the domain's language. The queue of a book is
 * its active reservations that hold no copy yet, in order of arrival.
 */
public interface ReservationRepository {

    /**
     * Saves the reservation and returns it as stored.
     *
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException if the reader already has an
     *         active reservation of the book (RN12), or the copy is already held by another active
     *         reservation; the database enforces both, so they also hold when two requests race
     */
    Reservation save(Reservation reservation);

    Optional<Reservation> findById(Long id);

    /** Listing, always paginated (RNF14). */
    PageResult<Reservation> findAll(ReservationFilter filter, PageQuery page);

    /** The active reservation that holds the given copy (RN13), empty when none does. */
    Optional<Reservation> findActiveByCopyId(Long copyId);

    /** The reservation at the head of the queue of the book: the oldest one still waiting for a copy. */
    Optional<Reservation> findFirstWaiting(Long bookId);

    /**
     * The place of a reservation in the queue of its book, counting from one; empty when it is not
     * waiting (it holds a copy, is closed or does not exist).
     */
    Optional<Long> queuePosition(Long reservationId);

    /**
     * The queue place of each given reservation, in one query; a reservation that is not waiting (it holds
     * a copy, is closed or does not exist) is absent from the map.
     */
    Map<Long, Long> queuePositions(Set<Long> reservationIds);

    /** How many active reservations the reader has, waiting or holding a copy (RN12). */
    long countActiveByReader(Long readerId);

    /**
     * The active reservations that hold a copy and whose pickup deadline ended before {@code today}
     * (RF19), the oldest deadline first.
     */
    List<Reservation> findPastDeadline(LocalDate today);
}
