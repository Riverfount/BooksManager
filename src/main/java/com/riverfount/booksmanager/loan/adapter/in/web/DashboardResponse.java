package com.riverfount.booksmanager.loan.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.loan.application.port.in.Dashboard;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

record DashboardResponse(
        @Schema(requiredMode = REQUIRED, example = "120", description = "Exemplares ativos, em qualquer status; "
                + "é o total de `GET /copies`")
        long totalCopies,
        @Schema(requiredMode = REQUIRED, example = "18", description = "Empréstimos ativos (ainda não devolvidos); "
                + "é o total de `GET /loans?status=ACTIVE`")
        long activeLoans,
        @Schema(requiredMode = REQUIRED, example = "4", description = "Empréstimos atrasados hoje; é o total de "
                + "`GET /loans?overdue=true` e de `/reports/overdue-loans`")
        long overdueLoans,
        @Schema(requiredMode = REQUIRED, example = "3", description = "Leitores com alguma suspensão em vigor "
                + "hoje, contados por leitor (duas suspensões do mesmo leitor contam uma vez)")
        long suspendedReaders,
        @Schema(requiredMode = REQUIRED, description = "O que cada categoria tem, em ordem de nome: a categoria "
                + "ativa sem livros vem com zero; a inativa só enquanto ainda tem livros ou exemplares")
        List<CategoryCopiesResponse> copiesByCategory) {

    static DashboardResponse from(Dashboard dashboard) {
        return new DashboardResponse(dashboard.totalCopies(), dashboard.activeLoans(), dashboard.overdueLoans(),
                dashboard.suspendedReaders(),
                dashboard.copiesByCategory().stream().map(CategoryCopiesResponse::from).toList());
    }
}
