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
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportExportController.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class, ReportExportCsvControllerTest.FixedClock.class})
class ReportExportCsvControllerTest {

    private static final String OVERDUE = "/api/v1/reports/overdue-loans/csv";
    private static final String MOST_BORROWED = "/api/v1/reports/most-borrowed-books/csv";

    /** "Today" is 24/10/2026 in São Paulo, so the file name is known. */
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

    private static String body(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @WithAdmin
    void shouldExportTheOverdueLoansAsAnAttachmentWithAPortugueseHeader() throws Exception {
        given(listOverdueLoansUseCase.list(any())).willReturn(
                new PageResult<>(List.of(late(42, 26, "Dom Casmurro"), late(43, 11, "Memórias")), 0, 100, 2));

        var result = mockMvc.perform(get(OVERDUE))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"overdue-loans-2026-10-24.csv\""))
                .andReturn();

        assertThat(body(result)).isEqualTo("﻿Empréstimo;Leitor;Nome do leitor;Exemplar;Patrimônio;Livro;"
                + "Título;Retirada;Prevista;Dias de atraso\r\n"
                + "42;20;Maria Silva;7;PAT-7;3;Dom Casmurro;2026-10-01;2026-10-15;26\r\n"
                + "43;20;Maria Silva;7;PAT-7;3;Memórias;2026-10-01;2026-10-15;11\r\n");
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

        assertThat(body(result)).contains("Livro 0").contains("Livro 1");
        var queries = ArgumentCaptor.forClass(ListOverdueLoansQuery.class);
        then(listOverdueLoansUseCase).should(org.mockito.Mockito.times(2)).list(queries.capture());
        assertThat(queries.getAllValues()).extracting(q -> q.page().size()).containsOnly(100);
        assertThat(queries.getAllValues()).extracting(q -> q.page().page()).containsExactly(0, 1);
    }

    @Test
    @WithAdmin
    void shouldNotLetATitleBecomeAFormula() throws Exception {
        given(listOverdueLoansUseCase.list(any())).willReturn(
                new PageResult<>(List.of(late(42, 26, "=HYPERLINK(\"x\")")), 0, 100, 1));

        var result = mockMvc.perform(get(OVERDUE)).andExpect(status().isOk()).andReturn();

        assertThat(body(result)).contains(";\"'=HYPERLINK(\"\"x\"\")\";");
    }

    @Test
    @WithAdmin
    void shouldExportOnlyTheHeaderWhenNoLoanIsLate() throws Exception {
        given(listOverdueLoansUseCase.list(any())).willReturn(new PageResult<>(List.of(), 0, 100, 0));

        var result = mockMvc.perform(get(OVERDUE)).andExpect(status().isOk()).andReturn();

        assertThat(body(result).lines().count()).isEqualTo(1);
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
    void shouldExportTheMostBorrowedBooksOfThePeriod() throws Exception {
        given(listMostBorrowedBooksUseCase.list(any())).willReturn(new PageResult<>(
                List.of(new MostBorrowedBook(3L, "Dom Casmurro", 12), new MostBorrowedBook(4L, "-Perigo; \"x\"", 9)),
                0, 100, 2));

        var result = mockMvc.perform(get(MOST_BORROWED).param("from", "2026-01-01").param("to", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"most-borrowed-books-2026-10-24.csv\""))
                .andReturn();

        assertThat(body(result)).isEqualTo("﻿Livro;Título;Total de empréstimos\r\n"
                + "3;Dom Casmurro;12\r\n"
                + "4;\"'-Perigo; \"\"x\"\"\";9\r\n");
        var query = ArgumentCaptor.forClass(ListMostBorrowedBooksQuery.class);
        then(listMostBorrowedBooksUseCase).should().list(query.capture());
        assertThat(query.getValue().from()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(query.getValue().to()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    @WithAdmin
    void aMissingPeriodShouldAnswer400() throws Exception {
        mockMvc.perform(get(MOST_BORROWED)).andExpect(status().isBadRequest());

        then(listMostBorrowedBooksUseCase).should(never()).list(any());
    }

    @Test
    @WithAdmin
    void aMalformedDateShouldAnswer400() throws Exception {
        mockMvc.perform(get(MOST_BORROWED).param("from", "ontem").param("to", "2026-12-31"))
                .andExpect(status().isBadRequest());
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
