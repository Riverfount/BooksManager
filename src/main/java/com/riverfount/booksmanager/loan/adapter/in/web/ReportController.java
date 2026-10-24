package com.riverfount.booksmanager.loan.adapter.in.web;

import com.riverfount.booksmanager.loan.application.port.in.ListMostBorrowedBooksQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListMostBorrowedBooksUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ListOverdueLoansQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListOverdueLoansUseCase;
import com.riverfount.booksmanager.shared.adapter.in.web.AdminOrOperator;
import com.riverfount.booksmanager.shared.adapter.in.web.CsvFile;
import com.riverfount.booksmanager.shared.adapter.in.web.PageResponse;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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
    private final ListMostBorrowedBooksUseCase listMostBorrowedBooksUseCase;
    private final Clock clock;

    ReportController(ListOverdueLoansUseCase listOverdueLoansUseCase,
            ListMostBorrowedBooksUseCase listMostBorrowedBooksUseCase, Clock clock) {
        this.listOverdueLoansUseCase = listOverdueLoansUseCase;
        this.listMostBorrowedBooksUseCase = listMostBorrowedBooksUseCase;
        this.clock = clock;
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

    @GetMapping("/most-borrowed-books")
    @Operation(summary = "Relatório de livros mais emprestados",
            description = "Lista os livros mais emprestados em um período (RF24), do mais para o menos emprestado, de "
                    + "forma paginada (máximo de 100 por página). Conta os empréstimos retirados de `from` a `to`, "
                    + "com as duas datas incluídas, somando os exemplares de cada livro; a renovação não conta como "
                    + "novo empréstimo e o empréstimo ainda ativo conta. Livros sem empréstimo no período não "
                    + "aparecem. O período é obrigatório, em ordem (`from` não depois de `to`) e de no máximo 366 "
                    + "dias. Empate no total desempata pelo título e depois pelo id, então a ordem é estável. A "
                    + "ordem é fixa; qualquer `sort` enviado é ignorado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página do relatório: o livro (id e título) e o total"),
            @ApiResponse(responseCode = "400",
                    description = "Período ausente, malformado, invertido ou maior que 366 dias, ou paginação "
                            + "inválida (inclusive tamanho acima de 100)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    PageResponse<MostBorrowedBookResponse> mostBorrowedBooks(
            @Parameter(description = "Primeiro dia do período, incluído (AAAA-MM-DD)", example = "2026-01-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Último dia do período, incluído (AAAA-MM-DD)", example = "2026-12-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Número da página, a partir de 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página, de 1 a 100")
            @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size) {
        return PageResponse.from(listMostBorrowedBooksUseCase.list(ListMostBorrowedBooksQuery.of(from, to, page, size)),
                MostBorrowedBookResponse::from);
    }

    @GetMapping("/overdue-loans/csv")
    @Operation(summary = "Exportar o relatório de empréstimos em atraso em CSV",
            description = "Baixa o relatório de empréstimos em atraso (RF29, RF23) como arquivo CSV, com as mesmas "
                    + "linhas e a mesma ordem de `GET /reports/overdue-loans`, mas todas de uma vez, sem "
                    + "paginação (no máximo " + CsvFile.MAX_ROWS + " linhas). O arquivo é UTF-8 com BOM, separado "
                    + "por `;`, com cabeçalho em português, e se chama `overdue-loans-AAAA-MM-DD.csv`. Texto que "
                    + "começa com `=`, `+`, `-` ou `@` sai com um `'` na frente, para a planilha não o tratar "
                    + "como fórmula.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Arquivo CSV",
                    content = @Content(mediaType = "text/csv", schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "422", description = "O relatório tem mais linhas que o máximo da exportação",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<byte[]> overdueLoansCsv() {
        var rows = CsvFile.collectAll(page -> listOverdueLoansUseCase
                .list(ListOverdueLoansQuery.of(page, CsvFile.PAGE_SIZE)).map(OverdueLoanResponse::from));
        var csv = CsvFile.render(
                List.of("Empréstimo", "Leitor", "Nome do leitor", "Exemplar", "Patrimônio", "Livro", "Título",
                        "Retirada", "Prevista", "Dias de atraso"),
                rows.stream().map(r -> Arrays.asList(r.loanId(), r.readerId(), r.readerName(), r.copyId(),
                        r.assetCode(), r.bookId(), r.bookTitle(), r.pickupDate(), r.dueDate(), r.daysLate()))
                        .toList());
        return CsvFile.response("overdue-loans", LocalDate.now(clock), csv);
    }

    @GetMapping("/most-borrowed-books/csv")
    @Operation(summary = "Exportar o relatório de livros mais emprestados em CSV",
            description = "Baixa o relatório de livros mais emprestados (RF29, RF24) como arquivo CSV, com as mesmas "
                    + "linhas, o mesmo período e a mesma ordem de `GET /reports/most-borrowed-books`, mas todas "
                    + "de uma vez, sem paginação (no máximo " + CsvFile.MAX_ROWS + " linhas). O período é "
                    + "obrigatório, em ordem e de no máximo 366 dias. O arquivo é UTF-8 com BOM, separado por `;`, "
                    + "com cabeçalho em português, e se chama `most-borrowed-books-AAAA-MM-DD.csv`. Texto que "
                    + "começa com `=`, `+`, `-` ou `@` sai com um `'` na frente, para a planilha não o tratar "
                    + "como fórmula.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Arquivo CSV",
                    content = @Content(mediaType = "text/csv", schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "400", description = "Período ausente, malformado, invertido ou maior que "
                    + "366 dias",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "O relatório tem mais linhas que o máximo da exportação",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<byte[]> mostBorrowedBooksCsv(
            @Parameter(description = "Primeiro dia do período, incluído (AAAA-MM-DD)", example = "2026-01-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Último dia do período, incluído (AAAA-MM-DD)", example = "2026-12-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var rows = CsvFile.collectAll(page -> listMostBorrowedBooksUseCase
                .list(ListMostBorrowedBooksQuery.of(from, to, page, CsvFile.PAGE_SIZE)));
        var csv = CsvFile.render(List.of("Livro", "Título", "Total de empréstimos"),
                rows.stream().map(b -> Arrays.asList(b.bookId(), b.title(), b.totalLoans())).toList());
        return CsvFile.response("most-borrowed-books", LocalDate.now(clock), csv);
    }
}
