package com.riverfount.booksmanager.shared.adapter.in.web;

import java.util.List;

/**
 * The rows of a report as the exports (CSV and PDF, RF29) write them: the header and one list of cells per
 * row, so both formats carry the same content in the same order.
 */
public record ReportTable(List<String> header, List<? extends List<?>> rows) {
}
