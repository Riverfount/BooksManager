package com.riverfount.booksmanager.loan.adapter.in.web;

import com.riverfount.booksmanager.loan.application.port.in.PayFineUseCase;
import com.riverfount.booksmanager.loan.application.port.in.WaiveFineCommand;
import com.riverfount.booksmanager.loan.application.port.in.WaiveFineUseCase;
import com.riverfount.booksmanager.shared.adapter.in.web.AdminOrOperator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AdminOrOperator
@RestController
@RequestMapping("/api/v1/fines")
@Tag(name = "Fines", description = "Pagamento e abono de multas por atraso (RF21).")
class FineController {

    private final PayFineUseCase payFineUseCase;
    private final WaiveFineUseCase waiveFineUseCase;

    FineController(PayFineUseCase payFineUseCase, WaiveFineUseCase waiveFineUseCase) {
        this.payFineUseCase = payFineUseCase;
        this.waiveFineUseCase = waiveFineUseCase;
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "Registrar pagamento da multa",
            description = "Marca a multa pendente como paga (HU10). Depois de paga, ela deixa de impedir novos "
                    + "empréstimos do leitor (RN05). Sem corpo. Uma multa resolvida (paga ou abonada) não pode ser "
                    + "paga de novo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Multa paga"),
            @ApiResponse(responseCode = "400", description = "Identificador inválido",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Multa não encontrada",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409",
                    description = "Outra requisição alterou a mesma multa ao mesmo tempo; tente novamente",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Multa já paga ou já abonada",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    FineResponse pay(@PathVariable Long id) {
        return FineResponse.from(payFineUseCase.pay(id));
    }

    @PostMapping("/{id}/waive")
    @Operation(summary = "Abonar multa",
            description = "Perdoa a multa pendente (HU10), com a justificativa, que é obrigatória e fica "
                    + "registrada. Depois de abonada, ela deixa de impedir novos empréstimos do leitor (RN05). Uma "
                    + "multa resolvida (paga ou abonada) não pode ser abonada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Multa abonada"),
            @ApiResponse(responseCode = "400",
                    description = "Requisição inválida: justificativa ausente, em branco ou com mais de 500 "
                            + "caracteres, ou identificador inválido",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Multa não encontrada",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409",
                    description = "Outra requisição alterou a mesma multa ao mesmo tempo; tente novamente",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Multa já paga ou já abonada",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    FineResponse waive(@PathVariable Long id, @Valid @RequestBody WaiveFineRequest request) {
        return FineResponse.from(waiveFineUseCase.waive(new WaiveFineCommand(id, request.reason())));
    }
}
