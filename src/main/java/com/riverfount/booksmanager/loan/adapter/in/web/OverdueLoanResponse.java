package com.riverfount.booksmanager.loan.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.loan.application.port.in.OverdueLoan;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

record OverdueLoanResponse(
        @Schema(requiredMode = REQUIRED, description = "Identificador do empréstimo", example = "42")
        Long loanId,
        @Schema(requiredMode = REQUIRED, description = "Id do leitor", example = "20")
        Long readerId,
        @Schema(description = "Nome do leitor (só o nome: os demais dados ficam na consulta do leitor)",
                example = "Maria Silva")
        String readerName,
        @Schema(requiredMode = REQUIRED, description = "Id do exemplar emprestado", example = "7")
        Long copyId,
        @Schema(description = "Código de patrimônio do exemplar", example = "PAT-0007")
        String assetCode,
        @Schema(description = "Id do livro do exemplar", example = "3")
        Long bookId,
        @Schema(description = "Título do livro do exemplar", example = "Dom Casmurro")
        String bookTitle,
        @Schema(requiredMode = REQUIRED, description = "Data de retirada (ISO-8601)", example = "2026-10-01")
        LocalDate pickupDate,
        @Schema(requiredMode = REQUIRED, description = "Data prevista de devolução (ISO-8601), já com as "
                + "renovações", example = "2026-10-15")
        LocalDate dueDate,
        @Schema(requiredMode = REQUIRED, description = "Dias de atraso, contados da data prevista até hoje",
                example = "26")
        long daysLate) {

    static OverdueLoanResponse from(OverdueLoan overdue) {
        var loan = overdue.loan();
        var copy = overdue.copy();
        return new OverdueLoanResponse(loan.getId(), loan.getReaderId(), overdue.readerName(), loan.getCopyId(),
                copy == null ? null : copy.assetCode(), copy == null ? null : copy.bookId(),
                copy == null ? null : copy.bookTitle(), loan.getPickupDate(), loan.getDueDate(),
                overdue.daysLate());
    }
}
