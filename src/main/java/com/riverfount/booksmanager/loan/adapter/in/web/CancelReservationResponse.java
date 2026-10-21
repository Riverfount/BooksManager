package com.riverfount.booksmanager.loan.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.loan.application.port.in.CancelReservationResult;
import io.swagger.v3.oas.annotations.media.Schema;

record CancelReservationResponse(
        @Schema(requiredMode = REQUIRED, description = "A reserva, já cancelada")
        ReservationResponse reservation,
        @Schema(description = "Reserva que recebeu o exemplar que a cancelada guardava (RF18): o exemplar fica "
                + "guardado para o próximo da fila; nula quando a cancelada não guardava exemplar, ou ninguém "
                + "esperava (o exemplar voltou a disponível)")
        ReservationSummary served) {

    static CancelReservationResponse from(CancelReservationResult result) {
        return new CancelReservationResponse(ReservationResponse.from(result.reservation(), null),
                result.served() == null ? null : ReservationSummary.from(result.served()));
    }
}
