package com.riverfount.booksmanager.loan.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.loan.domain.Suspension;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** A suspension as shown inside a loan's response. */
record SuspensionSummary(
        @Schema(requiredMode = REQUIRED, description = "Identificador da suspensão", example = "6")
        Long id,
        @Schema(requiredMode = REQUIRED, description = "Primeiro dia suspenso (o da devolução)",
                example = "2026-11-18")
        LocalDate startDate,
        @Schema(requiredMode = REQUIRED, description = "Dia em que o leitor volta a poder emprestar",
                example = "2026-11-21")
        LocalDate endDate) {

    static SuspensionSummary from(Suspension suspension) {
        return new SuspensionSummary(suspension.getId(), suspension.getStartDate(), suspension.getEndDate());
    }
}
