package com.riverfount.booksmanager.loan.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.loan.domain.ReturnCondition;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

record LoanResponse(
        @Schema(requiredMode = REQUIRED, description = "Identificador do empréstimo", example = "42")
        Long id,
        @Schema(requiredMode = REQUIRED, description = "Id do exemplar emprestado", example = "7")
        Long copyId,
        @Schema(requiredMode = REQUIRED, description = "Id do leitor", example = "20")
        Long readerId,
        @Schema(requiredMode = REQUIRED, description = "Data de retirada (ISO-8601)", example = "2026-11-10")
        LocalDate pickupDate,
        @Schema(requiredMode = REQUIRED, description = "Data prevista de devolução (ISO-8601)",
                example = "2026-11-24")
        LocalDate dueDate,
        @Schema(description = "Data da devolução; nula enquanto o exemplar não volta", example = "2026-11-20")
        LocalDate returnDate,
        @Schema(description = "Condição em que o exemplar voltou (GOOD, DAMAGED ou LOST); nula enquanto o "
                + "exemplar não volta", example = "GOOD")
        ReturnCondition returnCondition,
        @Schema(requiredMode = REQUIRED, description = "Quantas vezes o empréstimo foi renovado", example = "0")
        int renewals,
        @Schema(requiredMode = REQUIRED, description = "Se o empréstimo está ativo (ainda não devolvido)",
                example = "true")
        boolean active) {

    static LoanResponse from(Loan loan) {
        return new LoanResponse(loan.getId(), loan.getCopyId(), loan.getReaderId(), loan.getPickupDate(),
                loan.getDueDate(), loan.getReturnDate(), loan.getReturnCondition(), loan.getRenewals(),
                loan.isActive());
    }
}
