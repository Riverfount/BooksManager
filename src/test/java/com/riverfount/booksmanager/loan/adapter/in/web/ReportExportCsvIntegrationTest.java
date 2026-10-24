package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The CSV export of the reports (RF29) with the real security chain, clock and PostgreSQL: every page of the
 * report in its order, the accents, the neutralised formula and the access. Reads only, so each test runs in a
 * transaction that is rolled back.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class ReportExportCsvIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    private long categoryId;
    private long readerId;
    private int sequence;

    @BeforeEach
    void createACategoryAndAReader() {
        categoryId = jdbcTemplate.queryForObject(
                "insert into category (name) values ('Csv report test') returning id", Long.class);
        readerId = jdbcTemplate.queryForObject("""
                insert into reader (name, document, email, birth_date)
                values ('José da Conceição', 'CSV-DOC', 'r@example.com', date '1990-01-01') returning id
                """, Long.class);
    }

    private long book(String title) {
        return jdbcTemplate.queryForObject(
                "insert into book (isbn, title, category_id) values (?, ?, ?) returning id",
                Long.class, "97800000%05d".formatted(++sequence), title, categoryId);
    }

    /** A loan of a new copy of the book, picked up {@code lateByDays + 14} days ago and due {@code lateByDays} ago. */
    private void lateLoan(long bookId, int lateByDays) {
        var copyId = jdbcTemplate.queryForObject(
                "insert into copy (book_id, asset_code, status) values (?, ?, 'BORROWED') returning id",
                Long.class, bookId, "CSV-" + ++sequence);
        var due = TODAY.minusDays(lateByDays);
        jdbcTemplate.update("insert into loan (copy_id, reader_id, pickup_date, due_date) values (?, ?, ?, ?)",
                copyId, readerId, due.minusDays(14), due);
    }

    private String download(String url, String role) throws Exception {
        var token = "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", role, Instant.now(),
                Instant.now().plusSeconds(900));
        var result = mockMvc.perform(get(url).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andReturn();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void shouldExportEveryPageOfTheOverdueReportFromTheMostToTheLeastLate() throws Exception {
        var book = book("Memórias Póstumas");
        for (var late = 1; late <= 150; late++) {
            lateLoan(book, late);
        }

        List<String> lines = download("/api/v1/reports/overdue-loans/csv", "ADMIN").lines().toList();

        assertThat(lines).hasSize(151);
        assertThat(lines.getFirst()).startsWith("﻿Empréstimo;Leitor;Nome do leitor;");
        assertThat(lines.get(1)).contains(";José da Conceição;").contains(";Memórias Póstumas;").endsWith(";150");
        assertThat(lines.get(150)).endsWith(";1");
        assertThat(lines.stream().skip(1).map(l -> Integer.parseInt(l.substring(l.lastIndexOf(';') + 1))).toList())
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    void aTitleThatStartsWithAnEqualsSignShouldNotBecomeAFormula() throws Exception {
        lateLoan(book("=HYPERLINK(\"http://example.com\";\"clique\")"), 3);

        var csv = download("/api/v1/reports/overdue-loans/csv", "ADMIN");

        assertThat(csv).contains(";\"'=HYPERLINK(\"\"http://example.com\"\";\"\"clique\"\")\";");
    }

    @Test
    void shouldExportOnlyTheHeaderWhenNoLoanIsLate() throws Exception {
        assertThat(download("/api/v1/reports/overdue-loans/csv", "ADMIN").lines().count()).isEqualTo(1);
    }

    @Test
    void shouldExportTheMostBorrowedBooksOfThePeriod() throws Exception {
        var popular = book("Dom Casmurro");
        var other = book("Quincas Borba");
        lateLoan(popular, 5);
        lateLoan(popular, 6);
        lateLoan(other, 7);

        var csv = download("/api/v1/reports/most-borrowed-books/csv?from=" + TODAY.minusDays(60) + "&to=" + TODAY,
                "ADMIN");

        assertThat(csv.lines().toList()).containsExactly("﻿Livro;Título;Total de empréstimos",
                popular + ";Dom Casmurro;2", other + ";Quincas Borba;1");
    }

    @Test
    void aPeriodLongerThanAYearShouldAnswer400() throws Exception {
        var token = "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", "ADMIN", Instant.now(),
                Instant.now().plusSeconds(900));

        mockMvc.perform(get("/api/v1/reports/most-borrowed-books/csv")
                        .param("from", TODAY.minusDays(400).toString()).param("to", TODAY.toString())
                        .header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldExportTheCollectionStatus() throws Exception {
        var book = book("Dom Casmurro");
        jdbcTemplate.update("insert into copy (book_id, asset_code, status) values (?, 'CSV-A', 'AVAILABLE')", book);
        jdbcTemplate.update("insert into copy (book_id, asset_code, status) values (?, 'CSV-B', 'DAMAGED')", book);

        var lines = download("/api/v1/reports/collection-status/csv", "ADMIN").lines().toList();

        assertThat(lines.getFirst()).isEqualTo("﻿Situação;Exemplares");
        assertThat(lines).hasSize(8).contains("Disponível;" + countOf("AVAILABLE"), "Danificado;" + countOf("DAMAGED"));
        assertThat(lines.get(1)).startsWith("Total de exemplares ativos;");
        assertThat(lines.getLast()).startsWith("Exemplares inativos;");
    }

    private long countOf(String status) {
        return jdbcTemplate.queryForObject(
                "select count(*) from copy where status = ? and active", Long.class, status);
    }

    @Test
    void aRequestWithoutATokenShouldAnswer401() throws Exception {
        for (var url : List.of("/api/v1/reports/overdue-loans/csv", "/api/v1/reports/collection-status/csv",
                "/api/v1/reports/most-borrowed-books/csv?from=2026-01-01&to=2026-12-31")) {
            mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void anOperatorShouldBeRefusedEveryExportButStillReadTheReports() throws Exception {
        var token = "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", "OPERATOR", Instant.now(),
                Instant.now().plusSeconds(900));
        var period = "?from=" + TODAY.minusDays(60) + "&to=" + TODAY;

        for (var url : List.of("/api/v1/reports/overdue-loans/csv", "/api/v1/reports/overdue-loans/pdf",
                "/api/v1/reports/most-borrowed-books/csv" + period, "/api/v1/reports/most-borrowed-books/pdf" + period,
                "/api/v1/reports/collection-status/csv", "/api/v1/reports/collection-status/pdf")) {
            mockMvc.perform(get(url).header("Authorization", token))
                    .andExpect(status().isForbidden())
                    .andExpect(header().string("Content-Type", "application/problem+json"));
        }
        for (var url : List.of("/api/v1/reports/overdue-loans", "/api/v1/reports/most-borrowed-books" + period,
                "/api/v1/reports/collection-status")) {
            mockMvc.perform(get(url).header("Authorization", token)).andExpect(status().isOk());
        }
    }
}
