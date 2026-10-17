package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.WithAdmin;
import com.riverfount.booksmanager.WithOperator;
import com.riverfount.booksmanager.loan.application.port.in.RegisterLoanCommand;
import com.riverfount.booksmanager.loan.application.port.in.RegisterLoanUseCase;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.time.LocalDate;
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
}
