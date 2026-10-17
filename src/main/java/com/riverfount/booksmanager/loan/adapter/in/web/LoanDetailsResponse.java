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
        @Schema(description = "Nome do leitor", example = "Maria Silva")
        String readerName,
        @Schema(description = "Código de patrimônio do exemplar", example = "PAT-0007")
        String assetCode,
        @Schema(description = "Id do livro do exemplar", example = "3")
        Long bookId,
        @Schema(description = "Título do livro do exemplar", example = "Dom Casmurro")
        String bookTitle,
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
        @Schema(description = "Dias de atraso: com que o exemplar voltou (zero se foi no prazo) ou, para o empréstimo "
                + "ativo, até hoje; nulo enquanto o ativo está em dia (vencer hoje ainda é estar em dia)",
                example = "3")
        Long daysLate,
        @Schema(description = "Multa gerada pela devolução; nula quando não houve")
        FineSummary fine,
        @Schema(description = "Suspensão gerada pela devolução; nula quando não houve")
        SuspensionSummary suspension) {

    static LoanDetailsResponse from(LoanDetails details) {
        var loan = details.loan();
        var copy = details.copy();
        return new LoanDetailsResponse(loan.getId(), loan.getCopyId(), loan.getReaderId(), details.readerName(),
                copy == null ? null : copy.assetCode(), copy == null ? null : copy.bookId(),
                copy == null ? null : copy.bookTitle(), loan.getPickupDate(),
                loan.getDueDate(), loan.getReturnDate(), loan.getRenewals(), loan.isActive(),
                details.daysLate(),
                details.fine() == null ? null : FineSummary.from(details.fine()),
                details.suspension() == null ? null : SuspensionSummary.from(details.suspension()));
    }
}
