package com.riverfount.booksmanager.shared.adapter.in.web;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * The PDF export of the reports (RF29), shared by all modules: a landscape A4 page with the title, when it was
 * issued (São Paulo time), the filters used, the table with its header repeated on every page, the summary at
 * the end and the page number. The font is the Helvetica built into PDF (WinAnsi), which has the Portuguese
 * accents; a character outside it is left out of the file instead of failing the export. It is built in
 * memory, never on disk, and the row limit of {@link CsvFile#MAX_ROWS} keeps it small.
 */
public final class PdfReport {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter ISSUED_AT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color HEADER_BACKGROUND = new Color(0xE5, 0xE7, 0xEB);
    private static final int MIN_WEIGHT = 6;
    private static final int MAX_WEIGHT = 40;
    private static final int SAMPLED_ROWS = 200;

    private PdfReport() {
    }

    /**
     * Renders the report.
     *
     * @param title the title of the report
     * @param issuedAt the moment of the export
     * @param filters one line per filter used (for example the period); empty when the report has none
     * @param table the header and the rows
     * @param summary the line at the end (totals), or {@code null} for none
     */
    public static byte[] render(String title, Instant issuedAt, List<String> filters, ReportTable table,
            String summary) {
        var out = new ByteArrayOutputStream();
        var document = new Document(PageSize.A4.rotate(), 36, 36, 36, 48);
        try {
            var writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new PageNumber());
            document.open();
            document.add(new Paragraph(clean(title), font(16, Font.BOLD)));
            document.add(new Paragraph("Emitido em " + ISSUED_AT.format(issuedAt.atZone(ZONE)) + " ("
                    + ZONE.getId() + ")", font(9, Font.NORMAL)));
            for (var filter : filters) {
                document.add(new Paragraph(clean(filter), font(9, Font.NORMAL)));
            }
            document.add(table(table));
            if (summary != null) {
                var line = new Paragraph(clean(summary), font(10, Font.BOLD));
                line.setSpacingBefore(10);
                document.add(line);
            }
            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Não foi possível gerar o PDF", e);
        }
        return out.toByteArray();
    }

    /** The download: the file named after the report and the day, as an attachment. */
    public static ResponseEntity<byte[]> response(String report, LocalDate day, byte[] pdf) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(report + "-" + day + ".pdf").build().toString())
                .body(pdf);
    }

    private static PdfPTable table(ReportTable report) {
        var columns = report.header().size();
        var pdfTable = new PdfPTable(weights(report));
        pdfTable.setWidthPercentage(100);
        pdfTable.setSpacingBefore(10);
        pdfTable.setHeaderRows(1);
        pdfTable.setSplitRows(false);
        for (var title : report.header()) {
            var cell = new PdfPCell(new Phrase(clean(title), font(8, Font.BOLD)));
            cell.setBackgroundColor(HEADER_BACKGROUND);
            cell.setPadding(4);
            pdfTable.addCell(cell);
        }
        if (report.rows().isEmpty()) {
            // a table with only header rows is not drawn at all, so say that there is nothing
            var cell = new PdfPCell(new Phrase("Nenhum registro encontrado.", font(8, Font.NORMAL)));
            cell.setColspan(columns);
            cell.setPadding(6);
            pdfTable.addCell(cell);
        }
        for (var row : report.rows()) {
            for (var i = 0; i < columns; i++) {
                var cell = new PdfPCell(new Phrase(text(i < row.size() ? row.get(i) : null), font(8, Font.NORMAL)));
                cell.setPadding(3);
                pdfTable.addCell(cell);
            }
        }
        return pdfTable;
    }

    /** Wider columns for longer texts, within limits, so a title does not squeeze an id. */
    private static float[] weights(ReportTable report) {
        var weights = new float[report.header().size()];
        for (var i = 0; i < weights.length; i++) {
            var longest = report.header().get(i).length();
            for (var row : report.rows().stream().limit(SAMPLED_ROWS).toList()) {
                longest = Math.max(longest, i < row.size() ? text(row.get(i)).length() : 0);
            }
            weights[i] = Math.clamp(longest, MIN_WEIGHT, MAX_WEIGHT);
        }
        return weights;
    }

    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        return clean(value instanceof LocalDate day ? DAY.format(day) : value.toString());
    }

    /** Control characters have no glyph in the font; a line break inside a cell is kept. */
    private static String clean(String text) {
        return text.replaceAll("[\\p{Cntrl}&&[^\\n]]", " ");
    }

    private static Font font(float size, int style) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, style);
    }

    /** Writes "Página n" at the bottom right of every page. */
    private static final class PageNumber extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT,
                    new Phrase("Página " + writer.getPageNumber(), font(8, Font.NORMAL)),
                    document.right(), document.bottom() - 20, 0);
        }
    }
}
