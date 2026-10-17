package com.riverfount.booksmanager.loan.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.loan.domain.LibraryConfiguration;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The loan rules an operator may read (RF26): only the period and the limits that a screen needs to show.
 * The penalty policy and its values stay with the administrator, in {@link LibraryConfigurationResponse}.
 */
record LibraryRulesResponse(
        @Schema(requiredMode = REQUIRED, example = "14", description = "Prazo de empréstimo, em dias")
        int loanPeriodDays,
        @Schema(requiredMode = REQUIRED, example = "3", description = "Limite de empréstimos simultâneos por leitor")
        int loanLimit,
        @Schema(requiredMode = REQUIRED, example = "2", description = "Quantas vezes um empréstimo pode ser renovado")
        int renewalLimit,
        @Schema(requiredMode = REQUIRED, example = "3",
                description = "Prazo, em dias, para o leitor retirar um livro reservado")
        int reservationPickupDays) {

    static LibraryRulesResponse from(LibraryConfiguration configuration) {
        return new LibraryRulesResponse(configuration.getLoanPeriodDays(), configuration.getLoanLimit(),
                configuration.getRenewalLimit(), configuration.getReservationPickupDays());
    }
}
