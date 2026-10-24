package com.riverfount.booksmanager.shared.adapter.in.web;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * The CSV export of the reports (RF29), shared by all modules: UTF-8 with a byte order mark and {@code ;} as
 * the separator, which is what a spreadsheet in Brazilian Portuguese opens with the accents right. A text cell
 * that a spreadsheet could read as a formula is neutralised, because titles and names are typed by people.
 */
public final class CsvFile {

    /** The most rows one export carries, so a single request cannot exhaust the server. */
    public static final int MAX_ROWS = 10_000;

    /** The page size the export asks of the listing it reads. */
    public static final int PAGE_SIZE = 100;

    private static final MediaType MEDIA_TYPE = MediaType.parseMediaType("text/csv;charset=UTF-8");
    private static final String BYTE_ORDER_MARK = "﻿";
    private static final String SEPARATOR = ";";
    private static final String LINE_BREAK = "\r\n";
    private static final String FORMULA_STARTS = "=+-@\t\r";

    private CsvFile() {
    }

    /** Renders the header and the rows; a {@code null} cell is empty, any other is written by its text. */
    public static byte[] render(List<String> header, List<? extends List<?>> rows) {
        var csv = new StringBuilder(BYTE_ORDER_MARK);
        appendRow(csv, header);
        rows.forEach(row -> appendRow(csv, row));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Reads every page of a listing, in its own order, so the file has the same rows as the report on screen.
     * Refuses, before reading the rest, a listing with more than {@value #MAX_ROWS} rows.
     */
    public static <T> List<T> collectAll(IntFunction<PageResult<T>> pageLoader) {
        var all = new ArrayList<T>();
        for (int page = 0;; page++) {
            var result = pageLoader.apply(page);
            if (result.totalItems() > MAX_ROWS) {
                throw new BusinessRuleException("O relatório tem mais de " + MAX_ROWS
                        + " linhas e não cabe numa exportação; refine o filtro.");
            }
            all.addAll(result.items());
            if (result.items().isEmpty() || all.size() >= result.totalItems()) {
                return all;
            }
        }
    }

    /** The download: the file named after the report and the day, as an attachment. */
    public static ResponseEntity<byte[]> response(String report, LocalDate day, byte[] csv) {
        return ResponseEntity.ok()
                .contentType(MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(report + "-" + day + ".csv").build().toString())
                .body(csv);
    }

    private static void appendRow(StringBuilder csv, List<?> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                csv.append(SEPARATOR);
            }
            csv.append(cell(cells.get(i)));
        }
        csv.append(LINE_BREAK);
    }

    private static String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number || value instanceof LocalDate) {
            return value.toString();
        }
        var text = value.toString();
        if (!text.isEmpty() && FORMULA_STARTS.indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(SEPARATOR) || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
