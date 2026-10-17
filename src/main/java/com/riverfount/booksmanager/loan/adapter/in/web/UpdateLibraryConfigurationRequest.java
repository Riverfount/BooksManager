package com.riverfount.booksmanager.loan.adapter.in.web;

import com.riverfount.booksmanager.loan.domain.PenaltyPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Partial update of the library configuration: an omitted or null field keeps the
 * current value. The rules (positive values, the cap never below one day of fine,
 * two decimal places) are the domain's and answer 422 with a message that names
 * the field; a wrong type or an unknown policy answers 400. The whole numbers are
 * read strictly ({@link WholeNumberDeserializer}): a decimal like 1.5 is refused
 * instead of being cut to 1.
 */
record UpdateLibraryConfigurationRequest(
        @Schema(description = "Política de penalidade por atraso (opcional): FINE, SUSPENSION, BOTH ou NONE",
                example = "BOTH")
        PenaltyPolicy penaltyPolicy,
        @Schema(description = "Multa por dia de atraso, em reais (opcional): zero ou mais, com até duas casas "
                + "decimais", example = "1.50")
        BigDecimal dailyFineAmount,
        @Schema(description = "Teto da multa, em reais (opcional): maior que zero e nunca abaixo da multa de um dia",
                example = "15.00")
        BigDecimal fineCap,
        @Schema(description = "Dias de suspensão por dia de atraso (opcional): pelo menos 1", example = "1")
        @JsonDeserialize(using = WholeNumberDeserializer.class)
        Integer suspensionDaysPerDay,
        @Schema(description = "Teto da suspensão, em dias (opcional): pelo menos 1", example = "365")
        @JsonDeserialize(using = WholeNumberDeserializer.class)
        Integer suspensionCapDays,
        @Schema(description = "Prazo de empréstimo, em dias (opcional): pelo menos 1", example = "14")
        @JsonDeserialize(using = WholeNumberDeserializer.class)
        Integer loanPeriodDays,
        @Schema(description = "Limite de empréstimos simultâneos por leitor (opcional): pelo menos 1", example = "3")
        @JsonDeserialize(using = WholeNumberDeserializer.class)
        Integer loanLimit,
        @Schema(description = "Limite de renovações por empréstimo (opcional): zero ou mais", example = "2")
        @JsonDeserialize(using = WholeNumberDeserializer.class)
        Integer renewalLimit,
        @Schema(description = "Prazo de retirada de reservas, em dias (opcional): pelo menos 1", example = "3")
        @JsonDeserialize(using = WholeNumberDeserializer.class)
        Integer reservationPickupDays) {
}
