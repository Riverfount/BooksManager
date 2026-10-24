package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.WithAdmin;
import com.riverfount.booksmanager.WithOperator;
import com.riverfount.booksmanager.catalog.application.port.in.CopySummary;
import com.riverfount.booksmanager.loan.application.port.in.ListMostBorrowedBooksQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListMostBorrowedBooksUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ListOverdueLoansQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListOverdueLoansUseCase;
import com.riverfount.booksmanager.loan.application.port.in.MostBorrowedBook;
import com.riverfount.booksmanager.loan.application.port.in.OverdueLoan;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(ReportExportController.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class, ReportExportPdfControllerTest.FixedClock.class})
class ReportExportPdfControllerTest {

    private static final String OVERDUE = "/api/v1/reports/overdue-loans/pdf";
    private static final String MOST_BORROWED = "/api/v1/reports/most-borrowed-books/pdf";

    /** "Today" is 24/10/2026, 12:00 in São Paulo. */
    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-24T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListOverdueLoansUseCase listOverdueLoansUseCase;

    @MockitoBean
    private ListMostBorrowedBooksUseCase listMostBorrowedBooksUseCase;

    private static OverdueLoan late(long id, long daysLate, String title) {
        var loan = Loan.reconstitute(id, 7L, 20L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15), null, 0);
        return new OverdueLoan(loan, daysLate, "Maria Silva", new CopySummary(7L, "PAT-7", 3L, title));
    }

    private static String text(MvcResult result) throws Exception {
        var reader = new PdfReader(result.getResponse().getContentAsByteArray());
        try {
            var extractor = new PdfTextExtractor(reader);
            var all = new StringBuilder();
            for (var page = 1; page <= reader.getNumberOfPages(); page++) {
                all.append(extractor.getTextFromPage(page)).append('\n');
            }
            return all.toString();
        } finally {
            reader.close();
        }
    }

    @Test
    @WithAdmin
    void shouldExportTheOverdueLoansAsAPdfAttachment() throws Exception {
        given(listOverdueLoansUseCase.list(any())).willReturn(
                new PageResult<>(List.of(late(42, 26, "Dom Casmurro"), late(43, 11, "Memórias")), 0, 100, 2));

        var result = mockMvc.perform(get(OVERDUE))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"overdue-loans-2026-10-24.pdf\""))
                .andReturn();

        assertThat(text(result)).contains("Empréstimos em atraso")
                .contains("Emitido em 24/10/2026 12:00 (America/Sao_Paulo)")
                .contains("Maria Silva").contains("Dom Casmurro").contains("Memórias")
                .contains("01/10/2026").contains("15/10/2026")
                .contains("Total: 2 empréstimos em atraso");
    }

    @Test
    @WithAdmin
    void shouldReadEveryPageOfTheOverdueReportInItsOrder() throws Exception {
        given(listOverdueLoansUseCase.list(any())).willAnswer(call -> {
            ListOverdueLoansQuery query = call.getArgument(0);
            var page = query.page().page();
            return new PageResult<>(List.of(late(page + 1, 30 - page, "Livro " + page)), page, 100, 2);
        });

        var result = mockMvc.perform(get(OVERDUE)).andExpect(status().isOk()).andReturn();

        assertThat(text(result)).contains("Livro 0").contains("Livro 1");
        var queries = ArgumentCaptor.forClass(ListOverdueLoansQuery.class);
        then(listOverdueLoansUseCase).should(org.mockito.Mockito.times(2)).list(queries.capture());
        assertThat(queries.getAllValues()).extracting(q -> q.page().page()).containsExactly(0, 1);
    }

    @Test
    @WithAdmin
    void anOverdueReportBiggerThanTheLimitShouldAnswer422() throws Exception {
        given(listOverdueLoansUseCase.list(any())).willReturn(
                new PageResult<>(List.of(late(1, 1, "x")), 0, 100, 10_001));

        mockMvc.perform(get(OVERDUE))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("10000")));
    }

    @Test
    void anAnonymousRequestShouldAnswer401() throws Exception {
        mockMvc.perform(get(OVERDUE)).andExpect(status().isUnauthorized());

        then(listOverdueLoansUseCase).should(never()).list(any());
    }

    @Test
    @WithAdmin
    void shouldExportTheMostBorrowedBooksWithThePeriodAndTheTotals() throws Exception {
        given(listMostBorrowedBooksUseCase.list(any())).willReturn(new PageResult<>(
                List.of(new MostBorrowedBook(3L, "Dom Casmurro", 12), new MostBorrowedBook(4L, "São Bernardo", 9)),
                0, 100, 2));

        var result = mockMvc.perform(get(MOST_BORROWED).param("from", "2026-01-01").param("to", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"most-borrowed-books-2026-10-24.pdf\""))
                .andReturn();

        assertThat(text(result)).contains("Livros mais emprestados")
                .contains("Período: 01/01/2026 a 31/12/2026")
                .contains("Dom Casmurro").contains("São Bernardo")
                .contains("Total: 2 livros, 21 empréstimos");
        var query = ArgumentCaptor.forClass(ListMostBorrowedBooksQuery.class);
        then(listMostBorrowedBooksUseCase).should().list(query.capture());
        assertThat(query.getValue().from()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    @WithAdmin
    void aMissingPeriodShouldAnswer400() throws Exception {
        mockMvc.perform(get(MOST_BORROWED)).andExpect(status().isBadRequest());

        then(listMostBorrowedBooksUseCase).should(never()).list(any());
    }

    @Test
    @WithOperator
    void anOperatorShouldAnswer403OnEveryExport() throws Exception {
        for (var url : java.util.List.of(OVERDUE, MOST_BORROWED + "?from=2026-01-01&to=2026-12-31")) {
            mockMvc.perform(get(url))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        }

        then(listOverdueLoansUseCase).shouldHaveNoInteractions();
        then(listMostBorrowedBooksUseCase).shouldHaveNoInteractions();
    }
}
