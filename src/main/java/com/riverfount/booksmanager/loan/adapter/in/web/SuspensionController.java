package com.riverfount.booksmanager.loan.adapter.in.web;

import com.riverfount.booksmanager.loan.application.port.in.ListSuspensionsQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListSuspensionsUseCase;
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
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@AdminOrOperator
@RestController
@RequestMapping("/api/v1/suspensions")
@Tag(name = "Suspensions", description = "Consulta de suspensões de leitores por atraso (RF20, RF22).")
class SuspensionController {

    private final ListSuspensionsUseCase listSuspensionsUseCase;

    SuspensionController(ListSuspensionsUseCase listSuspensionsUseCase) {
        this.listSuspensionsUseCase = listSuspensionsUseCase;
    }

    @GetMapping
    @Operation(summary = "Listar suspensões",
            description = "Lista as suspensões de forma paginada (máximo de 100 por página), por leitor e por "
                    + "estarem ou não em vigor hoje (`inForce`). Uma suspensão em vigor vai do dia da devolução "
                    + "até o dia anterior ao `releaseDate`; uma que já terminou, sozinha ou encerrada antes, não "
                    + "está em vigor, e nenhuma rotina precisa fechá-la. Sem `inForce`, vêm todas. Por padrão, da "
                    + "mais recente para a mais antiga. Cada item traz a data de término e o `releaseDate`, o dia "
                    + "em que o leitor volta a poder emprestar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de suspensões"),
            @ApiResponse(responseCode = "400",
                    description = "Paginação, ordenação ou filtro inválidos (inclusive tamanho acima de 100)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    PageResponse<SuspensionResponse> list(
            @Parameter(description = "Só as suspensões deste leitor", example = "20")
            @RequestParam(required = false) Long readerId,
            @Parameter(description = "true: só as em vigor hoje; false: só as que não estão (já terminaram, foram "
                    + "encerradas antes ou ainda não começaram); sem o parâmetro, todas")
            @RequestParam(required = false) Boolean inForce,
            @Parameter(description = "Número da página, a partir de 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página, de 1 a 100")
            @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size,
            @Parameter(description = "Ordenação no formato campo[,asc|desc]; campos: id, startDate, endDate",
                    example = "startDate,desc")
            @RequestParam(defaultValue = ListSuspensionsQuery.DEFAULT_SORT) String sort) {
        var query = new ListSuspensionsQuery(readerId, inForce, PageQuery.of(page, size, sort));
        return PageResponse.from(listSuspensionsUseCase.list(query), SuspensionResponse::from);
    }
}
