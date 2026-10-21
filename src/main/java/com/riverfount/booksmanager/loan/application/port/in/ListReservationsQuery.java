package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.loan.domain.ReservationStatus;
import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import java.util.Objects;
import java.util.Set;

/**
 * Filters and pagination for listing reservations (RF16, RNF14). A null filter means "no filter on that
 * field": by reader, by book and by situation. The reservations of every situation are listed when no
 * status is given, so a closed one stays a call away.
 */
public record ListReservationsQuery(Long readerId, Long bookId, ReservationStatus status, PageQuery page) {

    public static final Set<String> SORTABLE_FIELDS = Set.of("id", "reservationDate", "pickupDeadline");
    public static final String DEFAULT_SORT = "id,desc";

    public ListReservationsQuery {
        Objects.requireNonNull(page, "page");
        if (!SORTABLE_FIELDS.contains(page.sortBy())) {
            throw new InvalidQueryException("não é possível ordenar reservas por: " + page.sortBy()
                    + " (use " + String.join(", ", SORTABLE_FIELDS.stream().sorted().toList()) + ")");
        }
    }
}
