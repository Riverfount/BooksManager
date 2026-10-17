package com.riverfount.booksmanager.loan.adapter.in.web;

import com.riverfount.booksmanager.loan.application.port.in.GetLibraryConfigurationUseCase;
import com.riverfount.booksmanager.shared.adapter.in.web.AdminOrOperator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The loan rules that the day to day screens need (RF26): the loan period and the limits, readable by the
 * operator. It is a controller of its own, apart from {@link LibraryConfigurationController}, which is for
 * administrators only: the rest of the configuration (the penalty policy and its values) never reaches an
 * operator, and nothing here changes anything.
 */
@AdminOrOperator
@RestController
@RequestMapping("/api/v1/library-configuration/rules")
@Tag(name = "Library rules",
        description = "O prazo e os limites de empréstimo, só para leitura, para administrador e operador (RF26).")
class LibraryRulesController {

    private final GetLibraryConfigurationUseCase getUseCase;

    LibraryRulesController(GetLibraryConfigurationUseCase getUseCase) {
        this.getUseCase = getUseCase;
    }

    @GetMapping
    @Operation(summary = "Consultar o prazo e os limites de empréstimo",
            description = "Devolve o prazo de empréstimo, os limites de empréstimos simultâneos e de renovações e o "
                    + "prazo de retirada de reservas, para a tela mostrar \"14 dias, limite de 3, renovado 0/2\". "
                    + "Vale para administrador e operador e é só leitura. A política de penalidade e os valores de "
                    + "multa e suspensão não vêm aqui: ficam na configuração completa, só para administrador.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Regras vigentes"))
    LibraryRulesResponse get() {
        return LibraryRulesResponse.from(getUseCase.get());
    }
}
