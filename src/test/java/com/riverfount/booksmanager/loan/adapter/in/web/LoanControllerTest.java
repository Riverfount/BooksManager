package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.WithAdmin;
import com.riverfount.booksmanager.WithOperator;
import com.riverfount.booksmanager.loan.application.port.in.GetLoanUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ListLoansQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListLoansUseCase;
import com.riverfount.booksmanager.loan.application.port.in.LoanDetails;
import com.riverfount.booksmanager.loan.application.port.in.LoanStatus;
import com.riverfount.booksmanager.loan.application.port.in.RegisterLoanCommand;
import com.riverfount.booksmanager.loan.application.port.in.RegisterLoanUseCase;
import com.riverfount.booksmanager.loan.application.port.in.RenewLoanCommand;
import com.riverfount.booksmanager.loan.application.port.in.RenewLoanUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ReturnLoanCommand;
import com.riverfount.booksmanager.loan.application.port.in.ReturnLoanResult;
import com.riverfount.booksmanager.loan.application.port.in.ReturnLoanUseCase;
import com.riverfount.booksmanager.loan.domain.Fine;
import com.riverfount.booksmanager.loan.domain.FineStatus;
import com.riverfount.booksmanager.loan.domain.Suspension;
import java.math.BigDecimal;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(LoanController.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class})
class LoanControllerTest {

    private static final String URL = "/api/v1/loans";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterLoanUseCase registerLoanUseCase;

    @MockitoBean
    private ReturnLoanUseCase returnLoanUseCase;

    @MockitoBean
    private RenewLoanUseCase renewLoanUseCase;

    @MockitoBean
    private GetLoanUseCase getLoanUseCase;

    @MockitoBean
    private ListLoansUseCase listLoansUseCase;

    private static Loan registered() {
        return Loan.reconstitute(42L, 7L, 20L, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 24), null, 0);
    }

    private ResultActions register(String json) throws Exception {
        return mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    @WithOperator
    void shouldRegisterTheLoanAndReturn201WithLocation() throws Exception {
        given(registerLoanUseCase.register(any())).willReturn(registered());

        register("{\"copyId\":7,\"readerId\":20}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/loans/42")))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.copyId").value(7))
                .andExpect(jsonPath("$.readerId").value(20))
                .andExpect(jsonPath("$.pickupDate").value("2026-11-10"))
                .andExpect(jsonPath("$.dueDate").value("2026-11-24"))
                .andExpect(jsonPath("$.returnDate").doesNotExist())
                .andExpect(jsonPath("$.renewals").value(0))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @WithAdmin
    void anAdministratorShouldAlsoRegisterTheLoan() throws Exception {
        given(registerLoanUseCase.register(any())).willReturn(registered());

        register("{\"copyId\":7,\"readerId\":20}").andExpect(status().isCreated());
    }

    @Test
    @WithOperator
    void shouldPassTheIdsToTheUseCase() throws Exception {
        given(registerLoanUseCase.register(any())).willReturn(registered());

        register("{\"copyId\":7,\"readerId\":20}").andExpect(status().isCreated());

        var captor = ArgumentCaptor.forClass(RegisterLoanCommand.class);
        then(registerLoanUseCase).should().register(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new RegisterLoanCommand(7L, 20L));
    }

    @Test
    @WithOperator
    void shouldIgnoreDatesSentByTheClient() throws Exception {
        given(registerLoanUseCase.register(any())).willReturn(registered());

        register("{\"copyId\":7,\"readerId\":20,\"dueDate\":\"2030-01-01\",\"pickupDate\":\"2030-01-01\"}")
                .andExpect(status().isCreated());

        var captor = ArgumentCaptor.forClass(RegisterLoanCommand.class);
        then(registerLoanUseCase).should().register(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new RegisterLoanCommand(7L, 20L));
    }

    @Test
    @WithOperator
    void shouldReturn400WhenTheCopyIsMissing() throws Exception {
        register("{\"readerId\":20}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));

        then(registerLoanUseCase).should(never()).register(any());
    }

    @Test
    @WithOperator
    void shouldReturn400WhenTheReaderIsMissing() throws Exception {
        register("{\"copyId\":7}").andExpect(status().isBadRequest());

        then(registerLoanUseCase).should(never()).register(any());
    }

    @Test
    @WithOperator
    void shouldReturn400ForAnIdThatIsNotANumber() throws Exception {
        register("{\"copyId\":\"abc\",\"readerId\":20}").andExpect(status().isBadRequest());
    }

    @Test
    @WithOperator
    void shouldReturn422WithTheReasonsWhenTheLoanIsRefused() throws Exception {
        given(registerLoanUseCase.register(any())).willThrow(new BusinessRuleException(
                "O empréstimo não pode ser feito: o leitor tem multa pendente; o leitor está suspenso."));

        register("{\"copyId\":7,\"readerId\":20}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value(
                        "O empréstimo não pode ser feito: o leitor tem multa pendente; o leitor está suspenso."));
    }

    @Test
    @WithOperator
    void shouldReturn404WhenTheCopyOrTheReaderDoesNotExist() throws Exception {
        given(registerLoanUseCase.register(any()))
                .willThrow(new ResourceNotFoundException("exemplar não encontrado: 7"));

        register("{\"copyId\":7,\"readerId\":20}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("exemplar não encontrado: 7"));
    }

    @Test
    @WithOperator
    void shouldReturn409WhenAnotherRequestChangedTheCopyFirst() throws Exception {
        given(registerLoanUseCase.register(any())).willThrow(new OptimisticLockingFailureException("stale"));

        register("{\"copyId\":7,\"readerId\":20}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value(containsString("tente novamente")));
    }

    @Test
    void shouldReturn401WithoutAuthentication() throws Exception {
        register("{\"copyId\":7,\"readerId\":20}").andExpect(status().isUnauthorized());

        then(registerLoanUseCase).should(never()).register(any());
    }

    // returning (RF12, HU08)

    private static final String RETURN_URL = URL + "/42/return";

    private static Loan returned() {
        return Loan.reconstitute(42L, 7L, 20L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 11, 18), 0);
    }

    private ResultActions giveBack() throws Exception {
        return mockMvc.perform(post(RETURN_URL).with(csrf()));
    }

    @Test
    @WithOperator
    void shouldReturnTheLoanAndAnswer200WithTheResultOnTime() throws Exception {
        var onTime = Loan.reconstitute(42L, 7L, 20L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 11, 15), 0);
        given(returnLoanUseCase.returnLoan(any())).willReturn(new ReturnLoanResult(onTime, 0, null, null));

        giveBack()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loan.id").value(42))
                .andExpect(jsonPath("$.loan.returnDate").value("2026-11-15"))
                .andExpect(jsonPath("$.loan.active").value(false))
                .andExpect(jsonPath("$.daysLate").value(0))
                .andExpect(jsonPath("$.fine").doesNotExist())
                .andExpect(jsonPath("$.suspension").doesNotExist());

        var captor = ArgumentCaptor.forClass(ReturnLoanCommand.class);
        then(returnLoanUseCase).should().returnLoan(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ReturnLoanCommand(42L));
    }

    @Test
    @WithAdmin
    void shouldShowTheDaysLateTheFineAndTheEndOfTheSuspension() throws Exception {
        var fine = Fine.reconstitute(5L, 42L, 20L, new BigDecimal("3"), FineStatus.PENDING, null);
        var suspension = Suspension.reconstitute(6L, 20L, 42L, LocalDate.of(2026, 11, 18),
                LocalDate.of(2026, 11, 21), null, null);
        given(returnLoanUseCase.returnLoan(any())).willReturn(new ReturnLoanResult(returned(), 3, fine, suspension));

        giveBack()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysLate").value(3))
                .andExpect(jsonPath("$.fine.id").value(5))
                .andExpect(jsonPath("$.fine.amount").value(3.00))
                .andExpect(jsonPath("$.fine.status").value("PENDING"))
                .andExpect(jsonPath("$.suspension.id").value(6))
                .andExpect(jsonPath("$.suspension.startDate").value("2026-11-18"))
                .andExpect(jsonPath("$.suspension.endDate").value("2026-11-21"));
    }

    @Test
    @WithOperator
    void returningAnAlreadyClosedLoanShouldAnswer422() throws Exception {
        given(returnLoanUseCase.returnLoan(any()))
                .willThrow(new BusinessRuleException("o empréstimo já foi encerrado"));

        giveBack()
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("o empréstimo já foi encerrado"));
    }

    @Test
    @WithOperator
    void returningALoanThatDoesNotExistShouldAnswer404() throws Exception {
        given(returnLoanUseCase.returnLoan(any()))
                .willThrow(new ResourceNotFoundException("empréstimo não encontrado: 42"));

        giveBack().andExpect(status().isNotFound());
    }

    @Test
    @WithOperator
    void returningWithAConflictingChangeShouldAnswer409() throws Exception {
        given(returnLoanUseCase.returnLoan(any())).willThrow(new OptimisticLockingFailureException("stale"));

        giveBack().andExpect(status().isConflict());
    }

    @Test
    @WithOperator
    void anIdThatIsNotANumberShouldAnswer400() throws Exception {
        mockMvc.perform(post(URL + "/abc/return").with(csrf())).andExpect(status().isBadRequest());

        then(returnLoanUseCase).should(never()).returnLoan(any());
    }

    @Test
    void returningWithoutAuthenticationShouldAnswer401() throws Exception {
        giveBack().andExpect(status().isUnauthorized());

        then(returnLoanUseCase).should(never()).returnLoan(any());
    }

    // renewing (RF13, HU09)

    private static final String RENEW_URL = URL + "/42/renew";

    private static Loan renewed() {
        return Loan.reconstitute(42L, 7L, 20L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 29), null, 1);
    }

    private ResultActions renew() throws Exception {
        return mockMvc.perform(post(RENEW_URL).with(csrf()));
    }

    @Test
    @WithOperator
    void shouldRenewTheLoanAndAnswer200WithTheNewDueDate() throws Exception {
        given(renewLoanUseCase.renew(any())).willReturn(renewed());

        renew()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.dueDate").value("2026-11-29"))
                .andExpect(jsonPath("$.renewals").value(1))
                .andExpect(jsonPath("$.active").value(true));

        var captor = ArgumentCaptor.forClass(RenewLoanCommand.class);
        then(renewLoanUseCase).should().renew(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new RenewLoanCommand(42L));
    }

    @Test
    @WithAdmin
    void anAdministratorShouldAlsoRenewTheLoan() throws Exception {
        given(renewLoanUseCase.renew(any())).willReturn(renewed());

        renew().andExpect(status().isOk());
    }

    @Test
    @WithOperator
    void aRefusedRenewalShouldAnswer422WithTheReason() throws Exception {
        given(renewLoanUseCase.renew(any())).willThrow(
                new BusinessRuleException("o limite de renovações (2) deste empréstimo já foi atingido"));

        renew()
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("o limite de renovações (2) deste empréstimo já foi atingido"));
    }

    @Test
    @WithOperator
    void renewingALoanThatDoesNotExistShouldAnswer404() throws Exception {
        given(renewLoanUseCase.renew(any())).willThrow(new ResourceNotFoundException("empréstimo não encontrado: 42"));

        renew().andExpect(status().isNotFound());
    }

    @Test
    @WithOperator
    void renewingWithAConflictingChangeShouldAnswer409() throws Exception {
        given(renewLoanUseCase.renew(any())).willThrow(new OptimisticLockingFailureException("stale"));

        renew().andExpect(status().isConflict());
    }

    @Test
    @WithOperator
    void renewingWithAnIdThatIsNotANumberShouldAnswer400() throws Exception {
        mockMvc.perform(post(URL + "/abc/renew").with(csrf())).andExpect(status().isBadRequest());

        then(renewLoanUseCase).should(never()).renew(any());
    }

    @Test
    void renewingWithoutAuthenticationShouldAnswer401() throws Exception {
        renew().andExpect(status().isUnauthorized());

        then(renewLoanUseCase).should(never()).renew(any());
    }

    // querying (RF14, HU30, RNF14)

    private static LoanDetails closedWithPenalty() {
        var fine = Fine.reconstitute(5L, 42L, 20L, new BigDecimal("3.00"), FineStatus.PENDING, null);
        var suspension = Suspension.reconstitute(6L, 20L, 42L, LocalDate.of(2026, 11, 18),
                LocalDate.of(2026, 11, 21), null, null);
        return new LoanDetails(returned(), fine, suspension);
    }

    @Test
    @WithOperator
    void shouldFindALoanByIdWithItsDatesAndPenalty() throws Exception {
        given(getLoanUseCase.findById(42L)).willReturn(java.util.Optional.of(closedWithPenalty()));

        mockMvc.perform(get(URL + "/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.pickupDate").value("2026-11-01"))
                .andExpect(jsonPath("$.dueDate").value("2026-11-15"))
                .andExpect(jsonPath("$.returnDate").value("2026-11-18"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.daysLate").value(3))
                .andExpect(jsonPath("$.fine.amount").value(3.00))
                .andExpect(jsonPath("$.fine.status").value("PENDING"))
                .andExpect(jsonPath("$.suspension.endDate").value("2026-11-21"));
    }

    @Test
    @WithOperator
    void anActiveLoanShouldHaveNoDaysLateNorPenalty() throws Exception {
        given(getLoanUseCase.findById(42L)).willReturn(java.util.Optional.of(new LoanDetails(registered(), null, null)));

        mockMvc.perform(get(URL + "/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.returnDate").doesNotExist())
                .andExpect(jsonPath("$.daysLate").doesNotExist())
                .andExpect(jsonPath("$.fine").doesNotExist())
                .andExpect(jsonPath("$.suspension").doesNotExist());
    }

    @Test
    @WithAdmin
    void findingALoanThatDoesNotExistShouldAnswer404() throws Exception {
        given(getLoanUseCase.findById(42L)).willReturn(java.util.Optional.empty());

        mockMvc.perform(get(URL + "/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("empréstimo não encontrado: 42"));
    }

    @Test
    @WithOperator
    void shouldListLoansPaginatedWithTheFiltersPassedToTheUseCase() throws Exception {
        given(listLoansUseCase.list(any())).willReturn(
                new PageResult<>(List.of(closedWithPenalty()), 1, 5, 11));

        mockMvc.perform(get(URL).param("readerId", "20").param("copyId", "7").param("status", "RETURNED")
                        .param("page", "1").param("size", "5").param("sort", "dueDate,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(42))
                .andExpect(jsonPath("$.items[0].fine.id").value(5))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalItems").value(11))
                .andExpect(jsonPath("$.totalPages").value(3));

        var captor = ArgumentCaptor.forClass(ListLoansQuery.class);
        then(listLoansUseCase).should().list(captor.capture());
        var query = captor.getValue();
        assertThat(query.readerId()).isEqualTo(20L);
        assertThat(query.copyId()).isEqualTo(7L);
        assertThat(query.status()).isEqualTo(LoanStatus.RETURNED);
        assertThat(query.page().page()).isEqualTo(1);
        assertThat(query.page().size()).isEqualTo(5);
        assertThat(query.page().sortBy()).isEqualTo("dueDate");
    }

    @Test
    @WithOperator
    void withoutParametersTheListingShouldBeUnfilteredAndShowTheMostRecentFirst() throws Exception {
        given(listLoansUseCase.list(any())).willReturn(new PageResult<>(List.of(), 0, 20, 0));

        mockMvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());

        var captor = ArgumentCaptor.forClass(ListLoansQuery.class);
        then(listLoansUseCase).should().list(captor.capture());
        var query = captor.getValue();
        assertThat(query.readerId()).isNull();
        assertThat(query.copyId()).isNull();
        assertThat(query.status()).isNull();
        assertThat(query.page().size()).isEqualTo(20);
        assertThat(query.page().sortBy()).isEqualTo("pickupDate");
    }

    @Test
    @WithOperator
    void aPageSizeAbove100ShouldAnswer400() throws Exception {
        mockMvc.perform(get(URL).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));

        then(listLoansUseCase).should(never()).list(any());
    }

    @Test
    @WithOperator
    void aSizeOf100ShouldBeAccepted() throws Exception {
        given(listLoansUseCase.list(any())).willReturn(new PageResult<>(List.of(), 0, 100, 0));

        mockMvc.perform(get(URL).param("size", "100")).andExpect(status().isOk());
    }

    @Test
    @WithOperator
    void anUnknownStatusShouldAnswer400() throws Exception {
        mockMvc.perform(get(URL).param("status", "OVERDUE")).andExpect(status().isBadRequest());

        then(listLoansUseCase).should(never()).list(any());
    }

    @Test
    @WithOperator
    void anUnsupportedSortFieldShouldAnswer400() throws Exception {
        mockMvc.perform(get(URL).param("sort", "readerId")).andExpect(status().isBadRequest());

        then(listLoansUseCase).should(never()).list(any());
    }

    @Test
    void listingWithoutAuthenticationShouldAnswer401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL + "/42")).andExpect(status().isUnauthorized());
    }
}
