package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.WithAdmin;
import com.riverfount.booksmanager.WithOperator;
import com.riverfount.booksmanager.loan.application.port.in.PayFineUseCase;
import com.riverfount.booksmanager.loan.application.port.in.WaiveFineCommand;
import com.riverfount.booksmanager.loan.application.port.in.WaiveFineUseCase;
import com.riverfount.booksmanager.loan.domain.Fine;
import com.riverfount.booksmanager.loan.domain.FineStatus;
import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.math.BigDecimal;
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

@WebMvcTest(FineController.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class})
class FineControllerTest {

    private static final String PAY_URL = "/api/v1/fines/5/pay";
    private static final String WAIVE_URL = "/api/v1/fines/5/waive";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PayFineUseCase payFineUseCase;

    @MockitoBean
    private WaiveFineUseCase waiveFineUseCase;

    private static Fine paid() {
        return Fine.reconstitute(5L, 42L, 20L, new BigDecimal("6.00"), FineStatus.PAID, null);
    }

    private static Fine waived() {
        return Fine.reconstitute(5L, 42L, 20L, new BigDecimal("6.00"), FineStatus.WAIVED, "Hospital stay");
    }

    private ResultActions pay() throws Exception {
        return mockMvc.perform(post(PAY_URL).with(csrf()));
    }

    private ResultActions waive(String json) throws Exception {
        return mockMvc.perform(post(WAIVE_URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // paying

    @Test
    @WithOperator
    void shouldPayTheFineAndAnswer200() throws Exception {
        given(payFineUseCase.pay(5L)).willReturn(paid());

        pay()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.loanId").value(42))
                .andExpect(jsonPath("$.readerId").value(20))
                .andExpect(jsonPath("$.amount").value(6.00))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.waiverReason").doesNotExist());
    }

    @Test
    @WithAdmin
    void anAdministratorShouldAlsoPay() throws Exception {
        given(payFineUseCase.pay(5L)).willReturn(paid());

        pay().andExpect(status().isOk());
    }

    @Test
    @WithOperator
    void payingAFineAlreadyResolvedShouldAnswer422() throws Exception {
        given(payFineUseCase.pay(5L)).willThrow(new BusinessRuleException("a multa já foi paga"));

        pay().andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.detail").value("a multa já foi paga"));
    }

    @Test
    @WithOperator
    void payingAFineThatDoesNotExistShouldAnswer404() throws Exception {
        given(payFineUseCase.pay(5L)).willThrow(new ResourceNotFoundException("multa não encontrada: 5"));

        pay().andExpect(status().isNotFound());
    }

    @Test
    @WithOperator
    void payingWithAConflictingChangeShouldAnswer409() throws Exception {
        given(payFineUseCase.pay(5L)).willThrow(new OptimisticLockingFailureException("stale"));

        pay().andExpect(status().isConflict());
    }

    @Test
    @WithOperator
    void payingWithAnIdThatIsNotANumberShouldAnswer400() throws Exception {
        mockMvc.perform(post("/api/v1/fines/abc/pay").with(csrf())).andExpect(status().isBadRequest());

        then(payFineUseCase).should(never()).pay(any());
    }

    @Test
    void payingWithoutAuthenticationShouldAnswer401() throws Exception {
        pay().andExpect(status().isUnauthorized());

        then(payFineUseCase).should(never()).pay(any());
    }

    // waiving

    @Test
    @WithOperator
    void shouldWaiveTheFineWithTheJustificationAndAnswer200() throws Exception {
        given(waiveFineUseCase.waive(any())).willReturn(waived());

        waive("{\"reason\":\"Hospital stay\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAIVED"))
                .andExpect(jsonPath("$.waiverReason").value("Hospital stay"));

        var captor = ArgumentCaptor.forClass(WaiveFineCommand.class);
        then(waiveFineUseCase).should().waive(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new WaiveFineCommand(5L, "Hospital stay"));
    }

    @Test
    @WithAdmin
    void anAdministratorShouldAlsoWaive() throws Exception {
        given(waiveFineUseCase.waive(any())).willReturn(waived());

        waive("{\"reason\":\"Hospital stay\"}").andExpect(status().isOk());
    }

    @Test
    @WithOperator
    void waivingWithoutAJustificationShouldAnswer400() throws Exception {
        waive("{}").andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
        waive("{\"reason\":\"   \"}").andExpect(status().isBadRequest());
        waive("{\"reason\":null}").andExpect(status().isBadRequest());

        then(waiveFineUseCase).should(never()).waive(any());
    }

    @Test
    @WithOperator
    void waivingWithATooLongJustificationShouldAnswer400() throws Exception {
        waive("{\"reason\":\"" + "x".repeat(Fine.MAX_WAIVER_REASON_LENGTH + 1) + "\"}")
                .andExpect(status().isBadRequest());

        then(waiveFineUseCase).should(never()).waive(any());
    }

    @Test
    @WithOperator
    void waivingWithoutABodyShouldAnswer400() throws Exception {
        mockMvc.perform(post(WAIVE_URL).with(csrf())).andExpect(status().isBadRequest());
    }

    @Test
    @WithOperator
    void waivingAFineAlreadyResolvedShouldAnswer422() throws Exception {
        given(waiveFineUseCase.waive(any())).willThrow(new BusinessRuleException("a multa já foi abonada"));

        waive("{\"reason\":\"Again\"}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("a multa já foi abonada"));
    }

    @Test
    @WithOperator
    void waivingAFineThatDoesNotExistShouldAnswer404() throws Exception {
        given(waiveFineUseCase.waive(any())).willThrow(new ResourceNotFoundException("multa não encontrada: 5"));

        waive("{\"reason\":\"Hospital stay\"}").andExpect(status().isNotFound());
    }

    @Test
    void waivingWithoutAuthenticationShouldAnswer401() throws Exception {
        waive("{\"reason\":\"Hospital stay\"}").andExpect(status().isUnauthorized());

        then(waiveFineUseCase).should(never()).waive(any());
    }
}
