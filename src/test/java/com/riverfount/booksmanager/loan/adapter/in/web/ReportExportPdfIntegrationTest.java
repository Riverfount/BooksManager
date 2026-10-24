package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.TestTokens;
import com.riverfount.booksmanager.TestcontainersConfiguration;
import com.riverfount.booksmanager.security.configuration.JwtProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
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
 * The PDF export of the reports (RF29) with the real security chain, clock and PostgreSQL: every page of the
 * report in its order over several PDF pages with the header repeated, the accents and the access. Reads only, so each test runs in a
 * transaction that is rolled back.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class ReportExportPdfIntegrationTest {

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

    private record Pdf(int pages, List<String> text) {
    }

    private Pdf download(String url, String role) throws Exception {
        var token = "Bearer " + TestTokens.signed(encoder, properties.issuer(), "1", role, Instant.now(),
                Instant.now().plusSeconds(900));
        var result = mockMvc.perform(get(url).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn();
        var reader = new PdfReader(result.getResponse().getContentAsByteArray());
        try {
            var extractor = new PdfTextExtractor(reader);
            var text = new java.util.ArrayList<String>();
            for (var page = 1; page <= reader.getNumberOfPages(); page++) {
                text.add(extractor.getTextFromPage(page));
            }
            return new Pdf(reader.getNumberOfPages(), text);
        } finally {
            reader.close();
        }
    }

    @Test
    void shouldExportAnOverdueReportThatBreaksIntoPagesWithTheHeaderRepeated() throws Exception {
        var book = book("Memórias Póstumas");
        for (var late = 1; late <= 150; late++) {
            lateLoan(book, late);
        }

        var pdf = download("/api/v1/reports/overdue-loans/pdf", "ADMIN");

        assertThat(pdf.pages()).isGreaterThan(1);
        pdf.text().forEach(page -> assertThat(page).contains("Nome do leitor").contains("Dias de atraso"));
        assertThat(pdf.text().getFirst()).contains("Empréstimos em atraso").contains("Emitido em ")
                .contains("José da Conceição").contains("Memórias Póstumas");
        assertThat(pdf.text().getLast()).contains("Total: 150 empréstimos em atraso");
    }

    @Test
    void shouldExportAnEmptyOverdueReport() throws Exception {
        var pdf = download("/api/v1/reports/overdue-loans/pdf", "ADMIN");

        assertThat(pdf.pages()).isEqualTo(1);
        assertThat(pdf.text().getFirst()).contains("Nenhum registro encontrado.")
                .contains("Total: 0 empréstimos em atraso");
    }

    @Test
    void shouldExportTheMostBorrowedBooksOfThePeriod() throws Exception {
        var popular = book("Dom Casmurro");
        lateLoan(popular, 5);
        lateLoan(popular, 6);

        var pdf = download("/api/v1/reports/most-borrowed-books/pdf?from=" + TODAY.minusDays(60) + "&to=" + TODAY,
                "ADMIN");

        assertThat(pdf.text().getFirst()).contains("Livros mais emprestados").contains("Período: ")
                .contains("Dom Casmurro").contains("Total: 1 livros, 2 empréstimos");
    }

    @Test
    void shouldExportTheCollectionStatus() throws Exception {
        jdbcTemplate.update("insert into copy (book_id, asset_code, status) values (?, 'PDF-A', 'AVAILABLE')",
                book("Dom Casmurro"));

        var pdf = download("/api/v1/reports/collection-status/pdf", "ADMIN");

        assertThat(pdf.text().getFirst()).contains("Situação do acervo").contains("Disponível")
                .contains("Total de exemplares ativos: ");
    }

    @Test
    void aRequestWithoutATokenShouldAnswer401() throws Exception {
        for (var url : List.of("/api/v1/reports/overdue-loans/pdf", "/api/v1/reports/collection-status/pdf",
                "/api/v1/reports/most-borrowed-books/pdf?from=2026-01-01&to=2026-12-31")) {
            mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
    }
}
