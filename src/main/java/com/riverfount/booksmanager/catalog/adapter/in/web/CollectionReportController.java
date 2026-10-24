package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.application.port.in.GetCollectionStatusUseCase;
import com.riverfount.booksmanager.catalog.domain.CopyStatus;
import com.riverfount.booksmanager.shared.adapter.in.web.AdminOrOperator;
import com.riverfount.booksmanager.shared.adapter.in.web.CsvFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AdminOrOperator
@RestController
@RequestMapping("/api/v1/reports/collection-status")
@Tag(name = "Reports", description = "Relatórios da biblioteca (RF23).")
class CollectionReportController {

    private final GetCollectionStatusUseCase getCollectionStatusUseCase;

    private final Clock clock;

    CollectionReportController(GetCollectionStatusUseCase getCollectionStatusUseCase, Clock clock) {
        this.getCollectionStatusUseCase = getCollectionStatusUseCase;
        this.clock = clock;
    }

    @GetMapping
    @Operation(summary = "Situação do acervo",
            description = "Devolve, numa chamada só, o total de exemplares ativos e quantos há em cada status "
                    + "(`AVAILABLE`, `BORROWED`, `RESERVED`, `DAMAGED` e `LOST`; um status sem exemplares vem com "
                    + "zero, então a soma por status é sempre o total). Os exemplares inativos (RN14) vêm à "
                    + "parte, em `inactiveCopies`, e não entram na contagem dos ativos.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Situação do acervo"))
    CollectionStatusResponse get() {
        return CollectionStatusResponse.from(getCollectionStatusUseCase.get());
    }

    @GetMapping("/csv")
    @Operation(summary = "Exportar a situação do acervo em CSV",
            description = "Baixa a situação do acervo (RF29, RF25) como arquivo CSV, com os mesmos números de "
                    + "`GET /reports/collection-status`: uma linha com o total de exemplares ativos, uma por status "
                    + "e uma com os exemplares inativos. O arquivo é UTF-8 com BOM, separado por `;`, com "
                    + "cabeçalho em português, e se chama `collection-status-AAAA-MM-DD.csv`.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Arquivo CSV",
            content = @Content(mediaType = "text/csv", schema = @Schema(type = "string"))))
    ResponseEntity<byte[]> getCsv() {
        var status = getCollectionStatusUseCase.get();
        var rows = new ArrayList<List<Object>>();
        rows.add(List.of("Total de exemplares ativos", status.totalActive()));
        status.activeByStatus().forEach((copyStatus, total) -> rows.add(List.of(label(copyStatus), total)));
        rows.add(List.of("Exemplares inativos", status.inactive()));
        return CsvFile.response("collection-status", LocalDate.now(clock),
                CsvFile.render(List.of("Situação", "Exemplares"), rows));
    }

    private static String label(CopyStatus status) {
        return switch (status) {
            case AVAILABLE -> "Disponível";
            case BORROWED -> "Emprestado";
            case RESERVED -> "Reservado";
            case DAMAGED -> "Danificado";
            case LOST -> "Extraviado";
        };
    }
}
