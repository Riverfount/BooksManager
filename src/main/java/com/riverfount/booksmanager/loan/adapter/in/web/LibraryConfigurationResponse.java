package com.riverfount.booksmanager.loan.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.loan.domain.LibraryConfiguration;
import com.riverfount.booksmanager.loan.domain.PenaltyPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** The rules of the library as the API shows them (RF26). The configuration is unique, so it has no id here. */
record LibraryConfigurationResponse(
        @Schema(requiredMode = REQUIRED, example = "FINE",
                description = "O que acontece com a devolução em atraso: FINE (multa), SUSPENSION (suspensão do "
                        + "leitor), BOTH (as duas) ou NONE (nada)")
        PenaltyPolicy penaltyPolicy,
        @Schema(requiredMode = REQUIRED, example = "1.00", description = "Valor da multa por dia de atraso, em reais")
        BigDecimal dailyFineAmount,
        @Schema(requiredMode = REQUIRED, example = "10.00", description = "Teto da multa, em reais")
        BigDecimal fineCap,
        @Schema(requiredMode = REQUIRED, example = "1", description = "Dias de suspensão por dia de atraso")
        int suspensionDaysPerDay,
        @Schema(requiredMode = REQUIRED, example = "365", description = "Teto da suspensão, em dias")
        int suspensionCapDays,
        @Schema(requiredMode = REQUIRED, example = "14", description = "Prazo de empréstimo, em dias")
        int loanPeriodDays,
        @Schema(requiredMode = REQUIRED, example = "3", description = "Limite de empréstimos simultâneos por leitor")
        int loanLimit,
        @Schema(requiredMode = REQUIRED, example = "2", description = "Quantas vezes um empréstimo pode ser renovado")
        int renewalLimit,
        @Schema(requiredMode = REQUIRED, example = "3",
                description = "Prazo, em dias, para o leitor retirar um livro reservado")
        int reservationPickupDays) {

    static LibraryConfigurationResponse from(LibraryConfiguration c) {
        return new LibraryConfigurationResponse(c.getPenaltyPolicy(), c.getDailyFineAmount(), c.getFineCap(),
                c.getSuspensionDaysPerDay(), c.getSuspensionCapDays(), c.getLoanPeriodDays(), c.getLoanLimit(),
                c.getRenewalLimit(), c.getReservationPickupDays());
    }
}
