package com.riverfount.booksmanager.loan.adapter.in.web;

import com.riverfount.booksmanager.loan.application.port.in.ListFinesQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListFinesUseCase;
import com.riverfount.booksmanager.loan.application.port.in.PayFineUseCase;
import com.riverfount.booksmanager.loan.application.port.in.WaiveFineCommand;
import com.riverfount.booksmanager.loan.application.port.in.WaiveFineUseCase;
import com.riverfount.booksmanager.loan.domain.FineStatus;
import com.riverfount.booksmanager.shared.adapter.in.web.AdminOrOperator;
import com.riverfount.booksmanager.shared.adapter.in.web.PageResponse;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@AdminOrOperator
@RestController
@RequestMapping("/api/v1/fines")
@Tag(name = "Fines", description = "Consulta, pagamento e abono de multas por atraso (RF21, RF22).")
class FineController {

    private final PayFineUseCase payFineUseCase;
    private final WaiveFineUseCase waiveFineUseCase;
    private final ListFinesUseCase listFinesUseCase;

    FineController(PayFineUseCase payFineUseCase, WaiveFineUseCase waiveFineUseCase,
            ListFinesUseCase listFinesUseCase) {
        this.payFineUseCase = payFineUseCase;
        this.waiveFineUseCase = waiveFineUseCase;
        this.listFinesUseCase = listFinesUseCase;
    }

    @GetMapping
    @Operation(summary = "Listar multas",
            description = "Lista as multas de forma paginada (máximo de 100 por página), por leitor e por "
                    + "situação: `PENDING` (impede novos empréstimos, RN05), `PAID` ou `WAIVED`. Sem filtro de "
                    + "situação, vêm todas. Por padrão, da mais recente para a mais antiga. Cada multa traz o "
                    + "`pendingAmount`, o valor ainda devido: o da multa enquanto está pendente e zero depois de "
                    + "paga ou abonada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de multas"),
            @ApiResponse(responseCode = "400",
                    description = "Paginação, ordenação ou filtro inválidos (inclusive tamanho acima de 100 e "
                            + "situação desconhecida)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    PageResponse<FineResponse> list(
            @Parameter(description = "Só as multas deste leitor", example = "20")
            @RequestParam(required = false) Long readerId,
            @Parameter(description = "PENDING, PAID ou WAIVED; sem o parâmetro, todas")
            @RequestParam(required = false) FineStatus status,
            @Parameter(description = "Número da página, a partir de 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página, de 1 a 100")
            @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size,
            @Parameter(description = "Ordenação no formato campo[,asc|desc]; campos: id, amount, status",
                    example = "id,desc")
            @RequestParam(defaultValue = ListFinesQuery.DEFAULT_SORT) String sort) {
        var query = new ListFinesQuery(readerId, status, PageQuery.of(page, size, sort));
        return PageResponse.from(listFinesUseCase.list(query), FineResponse::from);
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
