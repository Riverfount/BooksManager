package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.application.port.in.CollectionStatus;
import com.riverfount.booksmanager.catalog.application.port.in.GetCollectionStatusUseCase;
import com.riverfount.booksmanager.catalog.domain.CopyStatus;
import com.riverfount.booksmanager.shared.adapter.in.web.AdminOnly;
import com.riverfount.booksmanager.shared.adapter.in.web.CsvFile;
import com.riverfount.booksmanager.shared.adapter.in.web.PdfReport;
import com.riverfount.booksmanager.shared.adapter.in.web.ReportTable;
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

/**
 * The CSV and PDF export of the collection status report (RF29). Exporting is for the administrator only, so
 * these routes live apart from {@link CollectionReportController}, whose report on screen is also for the
 * operator.
 */
@AdminOnly
@RestController
@RequestMapping("/api/v1/reports/collection-status")
@Tag(name = "Reports", description = "Relatórios da biblioteca (RF23).")
class CollectionReportExportController {

    private final GetCollectionStatusUseCase getCollectionStatusUseCase;
    private final Clock clock;

    CollectionReportExportController(GetCollectionStatusUseCase getCollectionStatusUseCase, Clock clock) {
        this.getCollectionStatusUseCase = getCollectionStatusUseCase;
        this.clock = clock;
    }

    @GetMapping("/csv")
    @Operation(summary = "Exportar a situação do acervo em CSV",
            description = "Baixa a situação do acervo (RF29, RF25) como arquivo CSV, só para o administrador (o operador recebe 403), com os mesmos números de "
                    + "`GET /reports/collection-status`: uma linha com o total de exemplares ativos, uma por status "
                    + "e uma com os exemplares inativos. O arquivo é UTF-8 com BOM, separado por `;`, com "
                    + "cabeçalho em português, e se chama `collection-status-AAAA-MM-DD.csv`.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Arquivo CSV",
            content = @Content(mediaType = "text/csv", schema = @Schema(type = "string"))))
    ResponseEntity<byte[]> getCsv() {
        var table = table(getCollectionStatusUseCase.get());
        return CsvFile.response("collection-status", LocalDate.now(clock),
                CsvFile.render(table.header(), table.rows()));
    }

    @GetMapping("/pdf")
    @Operation(summary = "Exportar a situação do acervo em PDF",
            description = "Baixa a situação do acervo (RF29, RF25) como arquivo PDF, só para o administrador (o operador recebe 403), para imprimir ou guardar, com "
                    + "os mesmos números de `GET /reports/collection-status`: o total de exemplares ativos, um por "
                    + "status e os inativos à parte. O PDF traz o título, a data e a hora da emissão "
                    + "(`America/Sao_Paulo`), a tabela, o total no fim e o número da página. Se chama "
                    + "`collection-status-AAAA-MM-DD.pdf`.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Arquivo PDF",
            content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary"))))
    ResponseEntity<byte[]> getPdf() {
        var status = getCollectionStatusUseCase.get();
        var pdf = PdfReport.render("Situação do acervo", clock.instant(), List.of("Filtros: nenhum"), table(status),
                "Total de exemplares ativos: " + status.totalActive() + " (os inativos ficam à parte: "
                        + status.inactive() + ")");
        return PdfReport.response("collection-status", LocalDate.now(clock), pdf);
    }

    /** One line per item: the total, one per status and the inactive apart. */
    private static ReportTable table(CollectionStatus status) {
        var rows = new ArrayList<List<Object>>();
        rows.add(List.of("Total de exemplares ativos", status.totalActive()));
        status.activeByStatus().forEach((copyStatus, total) -> rows.add(List.of(label(copyStatus), total)));
        rows.add(List.of("Exemplares inativos", status.inactive()));
        return new ReportTable(List.of("Situação", "Exemplares"), rows);
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
