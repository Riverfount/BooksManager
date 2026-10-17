package com.riverfount.booksmanager.loan.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.loan.domain.Fine;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

record FineResponse(
        @Schema(requiredMode = REQUIRED, description = "Identificador da multa", example = "5")
        Long id,
        @Schema(requiredMode = REQUIRED, description = "Id do empréstimo que gerou a multa", example = "42")
        Long loanId,
        @Schema(requiredMode = REQUIRED, description = "Id do leitor que deve a multa", example = "20")
        Long readerId,
        @Schema(requiredMode = REQUIRED, description = "Valor da multa, em reais", example = "6.00")
        BigDecimal amount,
        @Schema(requiredMode = REQUIRED, description = "Situação da multa; só a pendente impede novos empréstimos",
                example = "PAID", allowableValues = {"PENDING", "PAID", "WAIVED"})
        String status,
        @Schema(requiredMode = REQUIRED, description = "Valor ainda pendente: o valor da multa enquanto ela está "
                + "pendente e zero depois de paga ou abonada", example = "6.00")
        BigDecimal pendingAmount,
        @Schema(description = "Justificativa do abono; só existe na multa abonada", example = "Internação do leitor")
        String waiverReason) {

    static FineResponse from(Fine fine) {
        return new FineResponse(fine.getId(), fine.getLoanId(), fine.getReaderId(), fine.getAmount(),
                fine.getStatus().name(), fine.isPending() ? fine.getAmount() : BigDecimal.ZERO.setScale(2),
                fine.getWaiverReason());
    }
}
