package com.riverfount.booksmanager.shared.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvFileTest {

    private static String text(byte[] csv) {
        return new String(csv, StandardCharsets.UTF_8);
    }

    @Test
    void shouldStartWithTheUtf8ByteOrderMarkSoSpreadsheetsReadTheAccents() {
        var csv = CsvFile.render(List.of("Título"), List.of(List.of("Dom Casmurro")));

        assertThat(Arrays.copyOf(csv, 3)).containsExactly((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(text(csv)).isEqualTo("﻿Título\r\nDom Casmurro\r\n");
    }

    @Test
    void shouldSeparateTheCellsWithSemicolonAndKeepAccentsAndNumbersAndDates() {
        var csv = CsvFile.render(List.of("Leitor", "Atraso", "Prevista"),
                List.of(List.of("José da Conceição", 26L, LocalDate.of(2026, 10, 15))));

        assertThat(text(csv)).isEqualTo("﻿Leitor;Atraso;Prevista\r\nJosé da Conceição;26;2026-10-15\r\n");
    }

    @Test
    void shouldWriteOnlyTheHeaderWhenThereAreNoRows() {
        assertThat(text(CsvFile.render(List.of("A", "B"), List.of()))).isEqualTo("﻿A;B\r\n");
    }

    @Test
    void shouldLeaveANullCellEmpty() {
        var row = new ArrayList<Object>(Arrays.asList("x", null, "z"));

        assertThat(text(CsvFile.render(List.of("A", "B", "C"), List.of(row)))).endsWith("x;;z\r\n");
    }

    @Test
    void shouldQuoteACellWithSeparatorQuoteOrLineBreakAndDoubleTheQuotes() {
        var csv = CsvFile.render(List.of("T"), List.of(List.of("a;b"), List.of("diz \"oi\""), List.of("linha1\nlinha2")));

        assertThat(text(csv)).isEqualTo("﻿T\r\n\"a;b\"\r\n\"diz \"\"oi\"\"\"\r\n\"linha1\nlinha2\"\r\n");
    }

    @Test
    void shouldNotLetATextCellBecomeAFormula() {
        for (var dangerous : List.of("=1+1", "+1", "-1", "@SUM(A1)", "\tcmd", "\rcmd")) {
            var csv = text(CsvFile.render(List.of("T"), List.of(List.of(dangerous))));

            assertThat(csv).as(dangerous).contains("'" + dangerous);
        }
    }

    @Test
    void shouldNeutraliseAFormulaBeforeQuotingIt() {
        var csv = CsvFile.render(List.of("T"), List.of(List.of("=HYPERLINK(\"http://x\";\"a\")")));

        assertThat(text(csv)).isEqualTo("﻿T\r\n\"'=HYPERLINK(\"\"http://x\"\";\"\"a\"\")\"\r\n");
    }

    @Test
    void shouldNeutraliseAFormulaInTheHeaderToo() {
        assertThat(text(CsvFile.render(List.of("=x"), List.of()))).isEqualTo("﻿'=x\r\n");
    }

    @Test
    void shouldNotTouchANegativeNumber() {
        assertThat(text(CsvFile.render(List.of("N"), List.of(List.of(-3L))))).endsWith("-3\r\n");
    }

    @Test
    void shouldCollectEveryPageOfTheListing() {
        var all = CsvFile.collectAll(page -> new PageResult<>(
                page < 2 ? List.of("p" + page + "a", "p" + page + "b") : List.of("p2a"), page, 100, 5));

        assertThat(all).containsExactly("p0a", "p0b", "p1a", "p1b", "p2a");
    }

    @Test
    void shouldCollectNothingFromAnEmptyListing() {
        assertThat(CsvFile.<String>collectAll(page -> new PageResult<>(List.of(), page, 100, 0))).isEmpty();
    }

    @Test
    void shouldRefuseToExportMoreRowsThanTheLimit() {
        assertThatThrownBy(() -> CsvFile.collectAll(
                page -> new PageResult<>(List.of("x"), page, 100, CsvFile.MAX_ROWS + 1L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(String.valueOf(CsvFile.MAX_ROWS));
    }

    @Test
    void shouldAcceptExactlyTheLimit() {
        var all = CsvFile.collectAll(page -> new PageResult<>(
                java.util.Collections.nCopies(CsvFile.PAGE_SIZE, "x"), page, CsvFile.PAGE_SIZE, CsvFile.MAX_ROWS));

        assertThat(all).hasSize(CsvFile.MAX_ROWS);
    }

    @Test
    void shouldNameTheFileAfterTheReportAndTheDate() {
        var response = CsvFile.response("overdue-loans", LocalDate.of(2026, 10, 24), new byte[] {1});

        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("overdue-loans-2026-10-24.csv");
        assertThat(response.getHeaders().getContentDisposition().isAttachment()).isTrue();
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(response.getBody()).containsExactly(1);
    }
}
