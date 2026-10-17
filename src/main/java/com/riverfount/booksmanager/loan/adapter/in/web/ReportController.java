package com.riverfount.booksmanager.loan.adapter.in.web;

import com.riverfount.booksmanager.loan.application.port.in.ListOverdueLoansQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListOverdueLoansUseCase;
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
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "Relatórios da biblioteca (RF23).")
class ReportController {

    private final ListOverdueLoansUseCase listOverdueLoansUseCase;

    ReportController(ListOverdueLoansUseCase listOverdueLoansUseCase) {
        this.listOverdueLoansUseCase = listOverdueLoansUseCase;
    }

    @GetMapping("/overdue-loans")
    @Operation(summary = "Relatório de empréstimos em atraso",
            description = "Lista os empréstimos ativos com a data prevista de devolução vencida (HU12), do maior "
                    + "para o menor atraso, de forma paginada (máximo de 100 por página). Cada item traz o leitor "
                    + "(id e nome), o exemplar (id, código de patrimônio e livro) e os dias de atraso, contados "
                    + "da data prevista até hoje. Um empréstimo que vence hoje ainda está em dia, e um já "
                    + "devolvido nunca aparece. A ordem é fixa; qualquer `sort` enviado é ignorado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página do relatório"),
            @ApiResponse(responseCode = "400", description = "Paginação inválida (inclusive tamanho acima de 100)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    PageResponse<OverdueLoanResponse> overdueLoans(
            @Parameter(description = "Número da página, a partir de 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página, de 1 a 100")
            @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size) {
        return PageResponse.from(listOverdueLoansUseCase.list(ListOverdueLoansQuery.of(page, size)),
                OverdueLoanResponse::from);
    }
}
