package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.riverfount.booksmanager.WithAdmin;
import com.riverfount.booksmanager.WithOperator;
import com.riverfount.booksmanager.catalog.application.port.in.CopySummary;
import com.riverfount.booksmanager.loan.application.port.in.GetOwnLoanQuery;
import com.riverfount.booksmanager.loan.application.port.in.GetOwnLoanUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ListOwnLoansQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListOwnLoansUseCase;
import com.riverfount.booksmanager.loan.application.port.in.LoanDetails;
import com.riverfount.booksmanager.loan.application.port.in.LoanStatus;
import com.riverfount.booksmanager.loan.application.port.in.RenewOwnLoanCommand;
import com.riverfount.booksmanager.loan.application.port.in.RenewOwnLoanUseCase;
import com.riverfount.booksmanager.loan.domain.Fine;
import com.riverfount.booksmanager.loan.domain.FineStatus;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.loan.domain.ReturnCondition;
import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The loans of the reader (Phase 6, RF13, RF14): the reader is the one of the token, what comes back is only what is
 * theirs (no copy, no reader data), the renewal takes no date, and the administrative profiles are kept out.
 */
@WebMvcTest(ReaderLoanController.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class})
class ReaderLoanControllerTest {

    private static final long ME = 42L;
    private static final LocalDate PICKUP = LocalDate.of(2026, 11, 1);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListOwnLoansUseCase listUseCase;

    @MockitoBean
    private GetOwnLoanUseCase getUseCase;

    @MockitoBean
    private RenewOwnLoanUseCase renewUseCase;

    private static RequestPostProcessor reader() {
        return jwt().jwt(token -> token.subject("5").claim("readerId", ME))
                .authorities(new SimpleGrantedAuthority("ROLE_READER"));
    }

    private static LoanDetails active(long id, int renewals, Long daysLate) {
        return new LoanDetails(Loan.reconstitute(id, 7L, ME, PICKUP, PICKUP.plusDays(14), null, renewals), null, null,
                "Maria Silva", new CopySummary(7L, "PAT-0007", 3L, "Dom Casmurro"), daysLate);
    }

    private static LoanDetails returnedWithFine(long id) {
        var loan = Loan.reconstitute(id, 7L, ME, PICKUP, PICKUP.plusDays(14), PICKUP.plusDays(17), 0,
                ReturnCondition.DAMAGED);
        var fine = Fine.reconstitute(5L, id, ME, new BigDecimal("3.00"), FineStatus.PENDING, null);
        return new LoanDetails(loan, fine, null, "Maria Silva", new CopySummary(7L, "PAT-0007", 3L, "Dom Casmurro"),
                3L);
    }

    // listing and looking

    @Test
    void shouldListTheLoansOfTheReaderWithoutTheCopyNorTheReaderData() throws Exception {
        given(listUseCase.list(any())).willReturn(new PageResult<>(List.of(returnedWithFine(9L)), 0, 20, 1));

        var body = mockMvc.perform(get("/api/v1/me/loans").with(reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(9))
                .andExpect(jsonPath("$.items[0].bookId").value(3))
                .andExpect(jsonPath("$.items[0].bookTitle").value("Dom Casmurro"))
                .andExpect(jsonPath("$.items[0].dueDate").value("2026-11-15"))
                .andExpect(jsonPath("$.items[0].returnDate").value("2026-11-18"))
                .andExpect(jsonPath("$.items[0].daysLate").value(3))
                .andExpect(jsonPath("$.items[0].fine.amount").value(3.00))
                .andExpect(jsonPath("$.items[0].fine.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> loan = JsonPath.read(body, "$.items[0]");
        assertThat(loan.keySet()).containsExactlyInAnyOrder("id", "bookId", "bookTitle", "pickupDate", "dueDate",
                "returnDate", "renewals", "active", "daysLate", "fine", "suspension");
        assertThat(body).doesNotContain("PAT-0007").doesNotContain("Maria Silva").doesNotContain("DAMAGED");
    }

    @Test
    void shouldListOnlyTheLoansOfTheReaderOfTheTokenAndPassTheFilters() throws Exception {
        given(listUseCase.list(any())).willReturn(new PageResult<>(List.of(), 0, 10, 0));

        mockMvc.perform(get("/api/v1/me/loans").with(reader()).param("status", "ACTIVE").param("overdue", "true")
                        .param("readerId", "99").param("copyId", "7").param("size", "10")
                        .param("sort", "dueDate,asc"))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(ListOwnLoansQuery.class);
        then(listUseCase).should().list(captor.capture());
        assertThat(captor.getValue().readerId()).isEqualTo(ME);
        assertThat(captor.getValue().status()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(captor.getValue().overdue()).isTrue();
        assertThat(captor.getValue().page().size()).isEqualTo(10);
    }

    @Test
    void shouldRefuseAPageBiggerThan100AnUnknownStatusAndAnUnknownSort() throws Exception {
        mockMvc.perform(get("/api/v1/me/loans").with(reader()).param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/me/loans").with(reader()).param("status", "NOPE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldShowALoanOfTheReader() throws Exception {
        given(getUseCase.get(new GetOwnLoanQuery(5L, ME))).willReturn(active(5L, 1, null));

        mockMvc.perform(get("/api/v1/me/loans/5").with(reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.renewals").value(1))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.fine").doesNotExist());
    }

    @Test
    void aLoanOfAnotherReaderShouldBeA404() throws Exception {
        given(getUseCase.get(any())).willThrow(new ResourceNotFoundException("empréstimo não encontrado: 9"));

        mockMvc.perform(get("/api/v1/me/loans/9").with(reader()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    // renewing

    @Test
    void shouldRenewTheLoanOfTheReaderOfTheTokenWithoutAnyBody() throws Exception {
        given(renewUseCase.renew(any())).willReturn(active(5L, 1, null));

        mockMvc.perform(post("/api/v1/me/loans/5/renew").with(csrf()).with(reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.renewals").value(1));

        then(renewUseCase).should().renew(new RenewOwnLoanCommand(5L, ME));
    }

    @Test
    void shouldAnswer404WhenRenewingWhatIsNotTheirsAnd422WithTheReasonOtherwise() throws Exception {
        given(renewUseCase.renew(new RenewOwnLoanCommand(9L, ME)))
                .willThrow(new ResourceNotFoundException("empréstimo não encontrado: 9"));
        given(renewUseCase.renew(new RenewOwnLoanCommand(6L, ME))).willThrow(new BusinessRuleException(
                "o livro tem uma reserva esperando na fila e o empréstimo não pode ser renovado"));

        mockMvc.perform(post("/api/v1/me/loans/9/renew").with(csrf()).with(reader()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/me/loans/6/renew").with(csrf()).with(reader()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(
                        "o livro tem uma reserva esperando na fila e o empréstimo não pode ser renovado"));
    }

    // who may call

    @Test
    @WithAdmin
    void anAdministratorMayNotUseTheLoansOfTheReader() throws Exception {
        mockMvc.perform(get("/api/v1/me/loans")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/me/loans/5")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/me/loans/5/renew").with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(listUseCase, getUseCase, renewUseCase);
    }

    @Test
    @WithOperator
    void anOperatorMayNotUseTheLoansOfTheReader() throws Exception {
        mockMvc.perform(get("/api/v1/me/loans")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/me/loans/5/renew").with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(listUseCase, renewUseCase);
    }

    @Test
    void anonymousIs401() throws Exception {
        mockMvc.perform(get("/api/v1/me/loans")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/me/loans/5/renew").with(csrf())).andExpect(status().isUnauthorized());
    }
}
