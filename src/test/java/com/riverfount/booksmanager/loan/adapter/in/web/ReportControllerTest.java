package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.WithAdmin;
import com.riverfount.booksmanager.WithOperator;
import com.riverfount.booksmanager.catalog.application.port.in.CopySummary;
import com.riverfount.booksmanager.loan.application.port.in.ListOverdueLoansQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListOverdueLoansUseCase;
import com.riverfount.booksmanager.loan.application.port.in.OverdueLoan;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportController.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class})
class ReportControllerTest {

    private static final String URL = "/api/v1/reports/overdue-loans";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListOverdueLoansUseCase listOverdueLoansUseCase;

    private static OverdueLoan late(long id, long daysLate) {
        var loan = Loan.reconstitute(id, 7L, 20L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15), null, 0);
        return new OverdueLoan(loan, daysLate, "Maria Silva", new CopySummary(7L, "PAT-7", 3L, "Dom Casmurro"));
    }

    @Test
    @WithOperator
    void shouldShowTheReaderTheCopyAndTheDaysLateOfEachLoan() throws Exception {
        given(listOverdueLoansUseCase.list(any())).willReturn(
                new PageResult<>(List.of(late(42, 26), late(43, 11)), 0, 20, 2));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].loanId").value(42))
                .andExpect(jsonPath("$.items[0].readerId").value(20))
                .andExpect(jsonPath("$.items[0].readerName").value("Maria Silva"))
                .andExpect(jsonPath("$.items[0].copyId").value(7))
                .andExpect(jsonPath("$.items[0].assetCode").value("PAT-7"))
                .andExpect(jsonPath("$.items[0].bookId").value(3))
                .andExpect(jsonPath("$.items[0].bookTitle").value("Dom Casmurro"))
                .andExpect(jsonPath("$.items[0].pickupDate").value("2026-10-01"))
                .andExpect(jsonPath("$.items[0].dueDate").value("2026-10-15"))
                .andExpect(jsonPath("$.items[0].daysLate").value(26))
                .andExpect(jsonPath("$.items[1].loanId").value(43))
                .andExpect(jsonPath("$.items[1].daysLate").value(11))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @WithAdmin
    void anAdministratorShouldAlsoSeeTheReport() throws Exception {
        given(listOverdueLoansUseCase.list(any())).willReturn(new PageResult<>(List.of(), 0, 20, 0));

        mockMvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @WithOperator
    void shouldPassThePageAndTheSizeToTheUseCase() throws Exception {
        given(listOverdueLoansUseCase.list(any())).willReturn(new PageResult<>(List.of(), 2, 5, 0));

        mockMvc.perform(get(URL).param("page", "2").param("size", "5")).andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(ListOverdueLoansQuery.class);
        then(listOverdueLoansUseCase).should().list(captor.capture());
        assertThat(captor.getValue().page().page()).isEqualTo(2);
        assertThat(captor.getValue().page().size()).isEqualTo(5);
        assertThat(captor.getValue().page().sortBy()).isEqualTo("dueDate");
    }

    @Test
    @WithOperator
    void withoutParametersTheFirstPageOf20ShouldBeAsked() throws Exception {
        given(listOverdueLoansUseCase.list(any())).willReturn(new PageResult<>(List.of(), 0, 20, 0));

        mockMvc.perform(get(URL)).andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(ListOverdueLoansQuery.class);
        then(listOverdueLoansUseCase).should().list(captor.capture());
        assertThat(captor.getValue().page().page()).isZero();
        assertThat(captor.getValue().page().size()).isEqualTo(20);
    }

    @Test
    @WithOperator
    void theOrderIsFixedSoASortSentByTheClientShouldBeIgnored() throws Exception {
        given(listOverdueLoansUseCase.list(any())).willReturn(new PageResult<>(List.of(), 0, 20, 0));

        mockMvc.perform(get(URL).param("sort", "readerId,desc")).andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(ListOverdueLoansQuery.class);
        then(listOverdueLoansUseCase).should().list(captor.capture());
        assertThat(captor.getValue().page().sortBy()).isEqualTo("dueDate");
    }

    @Test
    @WithOperator
    void aPageSizeAbove100ShouldAnswer400() throws Exception {
        mockMvc.perform(get(URL).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));

        then(listOverdueLoansUseCase).should(never()).list(any());
    }

    @Test
    @WithOperator
    void aPageSizeOf100ShouldBeAccepted() throws Exception {
        given(listOverdueLoansUseCase.list(any())).willReturn(new PageResult<>(List.of(), 0, 100, 0));

        mockMvc.perform(get(URL).param("size", "100")).andExpect(status().isOk());
    }

    @Test
    @WithOperator
    void aNegativePageOrAZeroSizeShouldAnswer400() throws Exception {
        mockMvc.perform(get(URL).param("page", "-1")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("size", "0")).andExpect(status().isBadRequest());

        then(listOverdueLoansUseCase).should(never()).list(any());
    }

    @Test
    void withoutAuthenticationTheAnswerShouldBe401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());

        then(listOverdueLoansUseCase).should(never()).list(any());
    }
}
