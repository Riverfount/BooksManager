package com.riverfount.booksmanager.loan.adapter.in.web;

import com.riverfount.booksmanager.loan.application.port.in.GetLibraryConfigurationUseCase;
import com.riverfount.booksmanager.loan.application.port.in.UpdateLibraryConfigurationCommand;
import com.riverfount.booksmanager.loan.application.port.in.UpdateLibraryConfigurationUseCase;
import com.riverfount.booksmanager.shared.adapter.in.web.AdminOnly;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AdminOnly
@RestController
@RequestMapping("/api/v1/library-configuration")
@Tag(name = "Library configuration",
        description = "Regras da biblioteca (política de penalidade, prazos e limites), só para administradores "
                + "(RF26).")
class LibraryConfigurationController {

    private final GetLibraryConfigurationUseCase getUseCase;
    private final UpdateLibraryConfigurationUseCase updateUseCase;

    LibraryConfigurationController(GetLibraryConfigurationUseCase getUseCase,
            UpdateLibraryConfigurationUseCase updateUseCase) {
        this.getUseCase = getUseCase;
        this.updateUseCase = updateUseCase;
    }

    @GetMapping
    @Operation(summary = "Consultar a configuração da biblioteca",
            description = "Devolve as regras vigentes: a política de penalidade por atraso e seus valores, o prazo "
                    + "de empréstimo, os limites de empréstimos e de renovações e o prazo de retirada de reservas. "
                    + "Só para administrador. A configuração é única: não tem id.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Configuração vigente"))
    LibraryConfigurationResponse get() {
        return LibraryConfigurationResponse.from(getUseCase.get());
    }

    @PatchMapping
    @Operation(summary = "Alterar a configuração da biblioteca",
            description = "Altera as regras da biblioteca, só para administrador. Um campo omitido ou nulo mantém o "
                    + "valor atual; campos desconhecidos, inclusive `id`, são ignorados. As regras são conferidas "
                    + "sobre o resultado, então um valor mantido também conta (subir a multa diária acima do teto "
                    + "atual é recusado); se algo for inválido, nada muda. A alteração vale só para os atrasos "
                    + "futuros: devoluções já registradas não são recalculadas, e o prazo dos empréstimos já feitos "
                    + "não muda.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Configuração atualizada"),
            @ApiResponse(responseCode = "400",
                    description = "Requisição inválida (política desconhecida ou valor de tipo errado, por exemplo)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422",
                    description = "Valor que quebra uma regra (a mensagem diz qual): prazo ou limite menor que 1, "
                            + "valor negativo, mais de duas casas decimais, teto da multa abaixo da multa diária",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    LibraryConfigurationResponse update(@Valid @RequestBody UpdateLibraryConfigurationRequest request) {
        var configuration = updateUseCase.update(new UpdateLibraryConfigurationCommand(request.penaltyPolicy(),
                request.dailyFineAmount(), request.fineCap(), request.suspensionDaysPerDay(),
                request.suspensionCapDays(), request.loanPeriodDays(), request.loanLimit(), request.renewalLimit(),
                request.reservationPickupDays()));
        return LibraryConfigurationResponse.from(configuration);
    }
}
