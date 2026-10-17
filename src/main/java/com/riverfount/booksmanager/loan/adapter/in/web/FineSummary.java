package com.riverfount.booksmanager.loan.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.loan.domain.Fine;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** A fine as shown inside a loan's response. */
record FineSummary(
        @Schema(requiredMode = REQUIRED, description = "Identificador da multa", example = "5")
        Long id,
        @Schema(requiredMode = REQUIRED, description = "Valor da multa, em reais", example = "3.00")
        BigDecimal amount,
        @Schema(requiredMode = REQUIRED, description = "Situação da multa", example = "PENDING",
                allowableValues = {"PENDING", "PAID", "WAIVED"})
        String status) {

    static FineSummary from(Fine fine) {
        return new FineSummary(fine.getId(), fine.getAmount(), fine.getStatus().name());
    }
}
