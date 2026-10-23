package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.WithAdmin;
import com.riverfount.booksmanager.WithOperator;
import com.riverfount.booksmanager.loan.application.port.in.GetLibraryConfigurationUseCase;
import com.riverfount.booksmanager.loan.application.port.in.UpdateLibraryConfigurationCommand;
import com.riverfount.booksmanager.loan.application.port.in.UpdateLibraryConfigurationUseCase;
import com.riverfount.booksmanager.loan.domain.LibraryConfiguration;
import com.riverfount.booksmanager.loan.domain.PenaltyPolicy;
import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(LibraryConfigurationController.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class})
class LibraryConfigurationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetLibraryConfigurationUseCase getUseCase;

    @MockitoBean
    private UpdateLibraryConfigurationUseCase updateUseCase;

    private static LibraryConfiguration initial() {
        return LibraryConfiguration.reconstitute(1L, PenaltyPolicy.FINE, new BigDecimal("1.00"),
                new BigDecimal("10.00"), 1, 365, 14, 3, 2, 3, 2);
    }

    private static MockHttpServletRequestBuilder edit(String json) {
        return patch("/api/v1/library-configuration").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    // reading

    @Test
    @WithAdmin
    void shouldGetTheConfigurationWithEveryValue() throws Exception {
        given(getUseCase.get()).willReturn(initial());

        mockMvc.perform(get("/api/v1/library-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.penaltyPolicy").value("FINE"))
                .andExpect(jsonPath("$.dailyFineAmount").value(1.00))
                .andExpect(jsonPath("$.fineCap").value(10.00))
                .andExpect(jsonPath("$.suspensionDaysPerDay").value(1))
                .andExpect(jsonPath("$.suspensionCapDays").value(365))
                .andExpect(jsonPath("$.loanPeriodDays").value(14))
                .andExpect(jsonPath("$.loanLimit").value(3))
                .andExpect(jsonPath("$.renewalLimit").value(2))
                .andExpect(jsonPath("$.reservationPickupDays").value(3))
                .andExpect(jsonPath("$.dueSoonNoticeDays").value(2));
    }

    @Test
    @WithAdmin
    void theResponseShouldNotExposeTheInternalId() throws Exception {
        given(getUseCase.get()).willReturn(initial());

        mockMvc.perform(get("/api/v1/library-configuration")).andExpect(jsonPath("$.id").doesNotExist());
    }

    // updating

    @Test
    @WithAdmin
    void shouldPatchAndHandTheInformedValuesToTheUseCase() throws Exception {
        given(updateUseCase.update(any())).willReturn(initial());

        mockMvc.perform(edit("""
                {"penaltyPolicy":"BOTH","dailyFineAmount":2.50,"fineCap":30.00,"suspensionDaysPerDay":2,
                 "suspensionCapDays":90,"loanPeriodDays":21,"loanLimit":5,"renewalLimit":0,
                 "reservationPickupDays":7, "dueSoonNoticeDays":5}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.penaltyPolicy").value("FINE"));

        var captor = ArgumentCaptor.forClass(UpdateLibraryConfigurationCommand.class);
        then(updateUseCase).should().update(captor.capture());
        var command = captor.getValue();
        assertThat(command.penaltyPolicy()).isEqualTo(PenaltyPolicy.BOTH);
        assertThat(command.dailyFineAmount()).isEqualByComparingTo("2.50");
        assertThat(command.fineCap()).isEqualByComparingTo("30.00");
        assertThat(command.suspensionDaysPerDay()).isEqualTo(2);
        assertThat(command.suspensionCapDays()).isEqualTo(90);
        assertThat(command.loanPeriodDays()).isEqualTo(21);
        assertThat(command.loanLimit()).isEqualTo(5);
        assertThat(command.renewalLimit()).isZero();
        assertThat(command.reservationPickupDays()).isEqualTo(7);
        assertThat(command.dueSoonNoticeDays()).isEqualTo(5);
    }

    @Test
    @WithAdmin
    void anOmittedOrNullFieldShouldReachTheUseCaseAsNullSoItKeepsTheCurrentValue() throws Exception {
        given(updateUseCase.update(any())).willReturn(initial());

        mockMvc.perform(edit("{\"loanPeriodDays\":21,\"loanLimit\":null}")).andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(UpdateLibraryConfigurationCommand.class);
        then(updateUseCase).should().update(captor.capture());
        var command = captor.getValue();
        assertThat(command.loanPeriodDays()).isEqualTo(21);
        assertThat(command.loanLimit()).isNull();
        assertThat(command.penaltyPolicy()).isNull();
        assertThat(command.dailyFineAmount()).isNull();
        assertThat(command.fineCap()).isNull();
        assertThat(command.renewalLimit()).isNull();
    }

    @Test
    @WithAdmin
    void anEmptyBodyIsAValidPatchThatChangesNothing() throws Exception {
        given(updateUseCase.update(any())).willReturn(initial());

        mockMvc.perform(edit("{}")).andExpect(status().isOk());
    }

    @Test
    @WithAdmin
    void shouldIgnoreTheIdAndAnyUnknownField() throws Exception {
        given(updateUseCase.update(any())).willReturn(initial());

        mockMvc.perform(edit("{\"id\":99,\"unknown\":true,\"loanPeriodDays\":21}")).andExpect(status().isOk());
    }

    @Test
    @WithAdmin
    void shouldReturn400ForAnUnknownPolicyANonNumericValueOrBrokenJson() throws Exception {
        for (var json : new String[] {"{\"penaltyPolicy\":\"MONEY\"}", "{\"penaltyPolicy\":\"fine\"}",
                "{\"loanPeriodDays\":\"abc\"}", "{\"dailyFineAmount\":\"abc\"}", "{\"loanLimit\":1.5}",
                "{\"loanLimit\":99999999999}", "{\"loanLimit\":\"5\"}",
                "{\"loanPeriodDays\":", "[1,2]"}) {
            mockMvc.perform(edit(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        }

        then(updateUseCase).should(never()).update(any());
    }

    @Test
    @WithAdmin
    void shouldReturn422WithTheMessageWhenAValueBreaksARule() throws Exception {
        given(updateUseCase.update(any()))
                .willThrow(new BusinessRuleException("o prazo de empréstimo deve ser de pelo menos 1 dia"));

        mockMvc.perform(edit("{\"loanPeriodDays\":0}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value(containsString("prazo de empréstimo")));
    }

    // only an administrator (RNF08)

    @Test
    @WithOperator
    void anOperatorGets403OnBothRoutesAndNothingReachesTheUseCases() throws Exception {
        mockMvc.perform(get("/api/v1/library-configuration"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mockMvc.perform(edit("{\"loanPeriodDays\":21}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        then(getUseCase).should(never()).get();
        then(updateUseCase).should(never()).update(any());
    }
}
