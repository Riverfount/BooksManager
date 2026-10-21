package com.riverfount.booksmanager.loan.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.loan.application.port.in.ReservationDetails;
import com.riverfount.booksmanager.loan.domain.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

record ReservationDetailsResponse(
        @Schema(requiredMode = REQUIRED, description = "Identificador da reserva", example = "42")
        Long id,
        @Schema(requiredMode = REQUIRED, description = "Id do livro reservado", example = "30")
        Long bookId,
        @Schema(description = "Título do livro reservado", example = "Dom Casmurro")
        String bookTitle,
        @Schema(requiredMode = REQUIRED, description = "Id do leitor", example = "20")
        Long readerId,
        @Schema(description = "Nome do leitor", example = "Maria Silva")
        String readerName,
        @Schema(description = "Id do exemplar guardado para o leitor; nulo enquanto a reserva espera na fila",
                example = "7")
        Long copyId,
        @Schema(description = "Código de patrimônio do exemplar guardado; nulo enquanto a reserva espera na fila",
                example = "PAT-0007")
        String assetCode,
        @Schema(requiredMode = REQUIRED, description = "Data da reserva (ISO-8601)", example = "2026-11-10")
        LocalDate reservationDate,
        @Schema(description = "Último dia para retirar o exemplar guardado (ISO-8601); nulo enquanto a reserva "
                + "espera na fila", example = "2026-11-13")
        LocalDate pickupDeadline,
        @Schema(requiredMode = REQUIRED, description = "Situação: ACTIVE, FULFILLED (retirada), CANCELLED ou "
                + "EXPIRED", example = "ACTIVE")
        ReservationStatus status,
        @Schema(description = "Lugar na fila do livro, a partir de 1; nulo quando a reserva não espera na fila "
                + "(já guarda um exemplar ou foi encerrada)", example = "3")
        Long queuePosition) {

    static ReservationDetailsResponse from(ReservationDetails details) {
        var reservation = details.reservation();
        var copy = details.copy();
        return new ReservationDetailsResponse(reservation.getId(), reservation.getBookId(), details.bookTitle(),
                reservation.getReaderId(), details.readerName(), reservation.getCopyId(),
                copy == null ? null : copy.assetCode(), reservation.getReservationDate(),
                reservation.getPickupDeadline(), reservation.getStatus(), details.queuePosition());
    }
}
