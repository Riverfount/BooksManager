package com.riverfount.booksmanager.shared.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

class PdfReportTest {

    private static final Instant ISSUED = Instant.parse("2026-10-24T15:30:00Z");
    private static final List<String> HEADER = List.of("Livro", "Título", "Total");

    private static byte[] pdf(List<? extends List<?>> rows, String summary) {
        return PdfReport.render("Livros mais emprestados", ISSUED, List.of("Período: 01/01/2026 a 31/12/2026"),
                new ReportTable(HEADER, rows), summary);
    }

    private static String text(byte[] pdf, int page) throws Exception {
        var reader = new PdfReader(pdf);
        try {
            return new PdfTextExtractor(reader).getTextFromPage(page);
        } finally {
            reader.close();
        }
    }

    private static int pages(byte[] pdf) throws Exception {
        var reader = new PdfReader(pdf);
        try {
            return reader.getNumberOfPages();
        } finally {
            reader.close();
        }
    }

    @Test
    void shouldProduceAValidPdf() {
        var bytes = pdf(List.of(List.of(3L, "Dom Casmurro", 12L)), "Total: 1 livro");

        assertThat(new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void shouldShowTheTitleTheIssueTimeInSaoPauloAndTheFilters() throws Exception {
        var text = text(pdf(List.of(List.of(3L, "Dom Casmurro", 12L)), "Total: 1 livro"), 1);

        assertThat(text).contains("Livros mais emprestados")
                .contains("Emitido em 24/10/2026 12:30 (America/Sao_Paulo)")
                .contains("Período: 01/01/2026 a 31/12/2026");
    }

    @Test
    void shouldShowTheHeaderTheRowsWithAccentsAndTheSummary() throws Exception {
        var rows = List.of(List.of(3L, "Memórias Póstumas de Brás Cubas", 12L), List.of(4L, "São Bernardo", 9L));

        var text = text(pdf(rows, "Total: 2 livros"), 1);

        assertThat(text).contains("Livro").contains("Título").contains("Total")
                .contains("Memórias Póstumas de Brás Cubas").contains("São Bernardo").contains("Total: 2 livros");
    }

    @Test
    void shouldWriteDatesTheBrazilianWayAndANullCellEmpty() throws Exception {
        var row = new ArrayList<Object>(Arrays.asList(1L, null, LocalDate.of(2026, 10, 15)));

        var text = text(pdf(List.of(row), null), 1);

        assertThat(text).contains("15/10/2026").doesNotContain("null").doesNotContain("2026-10-15");
    }

    @Test
    void shouldBreakALongReportIntoPagesRepeatingTheHeader() throws Exception {
        var rows = new ArrayList<List<Object>>();
        for (var i = 1; i <= 120; i++) {
            rows.add(List.of(i, "Livro número " + i, i));
        }

        var bytes = pdf(rows, "Total: 120 livros");

        assertThat(pages(bytes)).isGreaterThan(1);
        for (var page = 1; page <= pages(bytes); page++) {
            assertThat(text(bytes, page)).as("page " + page).contains("Título");
        }
        assertThat(text(bytes, 1)).contains("Livro número 1");
        assertThat(text(bytes, pages(bytes))).contains("Livro número 120").contains("Total: 120 livros");
    }

    @Test
    void shouldProduceAPdfWithOnlyTheHeaderWhenThereAreNoRows() throws Exception {
        var bytes = pdf(List.of(), "Total: 0 livros");

        assertThat(pages(bytes)).isEqualTo(1);
        assertThat(text(bytes, 1)).contains("Título").contains("Nenhum registro encontrado.")
                .contains("Total: 0 livros");
    }

    @Test
    void shouldNotFailOnACharacterTheFontDoesNotHave() throws Exception {
        var bytes = pdf(List.of(List.of(1L, "日本語 \u0000 Café", 1L)), null);

        assertThat(text(bytes, 1)).contains("Café");
    }

    @Test
    void shouldNameTheFileAfterTheReportAndTheDate() {
        var response = PdfReport.response("overdue-loans", LocalDate.of(2026, 10, 24), new byte[] {1});

        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("overdue-loans-2026-10-24.pdf");
        assertThat(response.getHeaders().getContentDisposition().isAttachment()).isTrue();
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
    }
}
