package com.riverfount.booksmanager.loan.adapter.in.web;

import com.riverfount.booksmanager.loan.application.port.in.GetDashboardUseCase;
import com.riverfount.booksmanager.shared.adapter.in.web.AdminOrOperator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AdminOrOperator
@RestController
@RequestMapping("/api/v1/reports/dashboard")
@Tag(name = "Reports", description = "Relatórios da biblioteca (RF23).")
class DashboardController {

    private final GetDashboardUseCase getDashboardUseCase;

    DashboardController(GetDashboardUseCase getDashboardUseCase) {
        this.getDashboardUseCase = getDashboardUseCase;
    }

    @GetMapping
    @Operation(summary = "Resumo do painel",
            description = "Devolve, numa chamada só, os números do painel: exemplares ativos, empréstimos ativos, "
                    + "empréstimos atrasados hoje, leitores suspensos hoje (contados por leitor, não por "
                    + "suspensão) e, por categoria, os livros ativos e os exemplares ativos (categoria sem livros "
                    + "vem com zero). Cada número é o total da listagem correspondente, exceto os leitores "
                    + "suspensos, e os exemplares das categorias somam o total de exemplares. \"Hoje\" é o dia "
                    + "do relógio da aplicação, em `America/Sao_Paulo`.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Números do painel"))
    DashboardResponse get() {
        return DashboardResponse.from(getDashboardUseCase.get());
    }
}
