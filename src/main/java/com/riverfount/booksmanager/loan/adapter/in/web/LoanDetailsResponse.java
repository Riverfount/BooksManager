package com.riverfount.booksmanager.loan.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.loan.application.port.in.LoanDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

record LoanDetailsResponse(
        @Schema(requiredMode = REQUIRED, description = "Identificador do empréstimo", example = "42")
        Long id,
        @Schema(requiredMode = REQUIRED, description = "Id do exemplar emprestado", example = "7")
        Long copyId,
        @Schema(requiredMode = REQUIRED, description = "Id do leitor", example = "20")
        Long readerId,
        @Schema(requiredMode = REQUIRED, description = "Data de retirada (ISO-8601)", example = "2026-11-01")
        LocalDate pickupDate,
        @Schema(requiredMode = REQUIRED, description = "Data prevista de devolução (ISO-8601), já com as "
                + "renovações", example = "2026-11-15")
        LocalDate dueDate,
        @Schema(description = "Data da devolução; nula enquanto o exemplar não volta", example = "2026-11-18")
        LocalDate returnDate,
        @Schema(requiredMode = REQUIRED, description = "Quantas vezes o empréstimo foi renovado", example = "0")
        int renewals,
        @Schema(requiredMode = REQUIRED, description = "Se o empréstimo está ativo (ainda não devolvido)",
                example = "false")
        boolean active,
        @Schema(description = "Dias de atraso com que o exemplar voltou; nulo enquanto o empréstimo está ativo",
                example = "3")
        Long daysLate,
        @Schema(description = "Multa gerada pela devolução; nula quando não houve")
        FineSummary fine,
        @Schema(description = "Suspensão gerada pela devolução; nula quando não houve")
        SuspensionSummary suspension) {

    static LoanDetailsResponse from(LoanDetails details) {
        var loan = details.loan();
        return new LoanDetailsResponse(loan.getId(), loan.getCopyId(), loan.getReaderId(), loan.getPickupDate(),
                loan.getDueDate(), loan.getReturnDate(), loan.getRenewals(), loan.isActive(),
                loan.isReturned() ? loan.daysLate(loan.getReturnDate()) : null,
                details.fine() == null ? null : FineSummary.from(details.fine()),
                details.suspension() == null ? null : SuspensionSummary.from(details.suspension()));
    }
}
