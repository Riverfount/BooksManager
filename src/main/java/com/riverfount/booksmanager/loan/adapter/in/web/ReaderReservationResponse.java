package com.riverfount.booksmanager.loan.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.loan.application.port.in.ReservationDetails;
import com.riverfount.booksmanager.loan.domain.Reservation;
import com.riverfount.booksmanager.loan.domain.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * A reservation as its own reader sees it (Phase 6): the book, where it stands, the place in the queue and the last
 * day to pick it up. There is nothing about the copy kept for the reader (its id or its asset code): the desk finds it
 * by the reader. Nor about the reader, since they are the one asking.
 */
record ReaderReservationResponse(
        @Schema(requiredMode = REQUIRED, description = "Identificador da reserva", example = "42")
        Long id,
        @Schema(requiredMode = REQUIRED, description = "Id do livro reservado", example = "30")
        Long bookId,
        @Schema(description = "Título do livro reservado", example = "Dom Casmurro")
        String bookTitle,
        @Schema(requiredMode = REQUIRED, description = "Data da reserva (ISO-8601)", example = "2026-11-10")
        LocalDate reservationDate,
        @Schema(description = "Último dia para retirar o exemplar guardado (ISO-8601); nulo enquanto a reserva "
                + "espera na fila e depois de a reserva ser encerrada. Quando vem preenchido, há um exemplar guardado para o leitor.",
                example = "2026-11-13")
        LocalDate pickupDeadline,
        @Schema(requiredMode = REQUIRED, description = "Situação: ACTIVE, FULFILLED (retirada), CANCELLED ou "
                + "EXPIRED", example = "ACTIVE")
        ReservationStatus status,
        @Schema(description = "Lugar na fila do livro, a partir de 1; nulo quando a reserva não espera na fila "
                + "(já guarda um exemplar ou foi encerrada)", example = "3")
        Long queuePosition) {

    /**
     * A reservation that was cancelled, picked up or expired keeps its old deadline in the database, but "pick it up by"
     * only means something for a reservation that is still active: the reader is never shown a date for a closed one.
     */
    private static LocalDate pickupDeadlineToShow(Reservation reservation) {
        return reservation.isActive() ? reservation.getPickupDeadline() : null;
    }

    static ReaderReservationResponse from(ReservationDetails details) {
        var reservation = details.reservation();
        return new ReaderReservationResponse(reservation.getId(), reservation.getBookId(), details.bookTitle(),
                reservation.getReservationDate(), pickupDeadlineToShow(reservation), reservation.getStatus(),
                details.queuePosition());
    }
}
