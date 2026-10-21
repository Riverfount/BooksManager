package com.riverfount.booksmanager.loan.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.loan.domain.Reservation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** A reservation as shown inside a return's response: the one that got the returned copy. */
record ReservationSummary(
        @Schema(requiredMode = REQUIRED, description = "Identificador da reserva", example = "42")
        Long id,
        @Schema(requiredMode = REQUIRED, description = "Id do leitor que vai retirar o exemplar", example = "21")
        Long readerId,
        @Schema(requiredMode = REQUIRED, description = "Id do exemplar guardado para o leitor", example = "7")
        Long copyId,
        @Schema(requiredMode = REQUIRED, description = "Último dia para retirar o exemplar (ISO-8601)",
                example = "2026-11-13")
        LocalDate pickupDeadline) {

    static ReservationSummary from(Reservation reservation) {
        return new ReservationSummary(reservation.getId(), reservation.getReaderId(), reservation.getCopyId(),
                reservation.getPickupDeadline());
    }
}
