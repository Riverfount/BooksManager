package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.catalog.application.port.in.CopySummary;
import com.riverfount.booksmanager.loan.domain.Reservation;

/**
 * A reservation with what a screen needs to show it (RF16): the name of the reader, the title of the book
 * and, when it holds one, the summary of the copy kept for the reader. {@code queuePosition} is the place in
 * the queue of the book, counting from one, and only for a reservation that waits for a copy; it is null when
 * the reservation holds a copy or is closed. The names and the copy are null only if their module no longer
 * knows them, which does not happen for a reservation that exists.
 */
public record ReservationDetails(Reservation reservation, Long queuePosition, String readerName, String bookTitle,
        CopySummary copy) {
}
