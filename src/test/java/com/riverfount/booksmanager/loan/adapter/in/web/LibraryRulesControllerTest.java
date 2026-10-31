package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.WithAdmin;
import com.riverfount.booksmanager.WithOperator;
import com.riverfount.booksmanager.loan.application.port.in.GetLibraryConfigurationUseCase;
import com.riverfount.booksmanager.loan.domain.LibraryConfiguration;
import com.riverfount.booksmanager.loan.domain.PenaltyPolicy;
import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The part of the library configuration the operator may read (RF26): the loan period and the limits, enough to
 * show "14 days, limit of 3, renewed 0/2", and nothing of the penalty policy.
 */
@WebMvcTest(LibraryRulesController.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class})
class LibraryRulesControllerTest {

    private static final String URL = "/api/v1/library-configuration/rules";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetLibraryConfigurationUseCase getUseCase;

    private void givenTheConfiguration() {
        given(getUseCase.get()).willReturn(LibraryConfiguration.reconstitute(1L, PenaltyPolicy.FINE,
                new BigDecimal("1.00"), new BigDecimal("10.00"), 1, 365, 14, 3, 2, 3, 2, "PAT"));
    }

    @Test
    @WithOperator
    void anOperatorShouldReadTheLoanRules() throws Exception {
        givenTheConfiguration();

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanPeriodDays").value(14))
                .andExpect(jsonPath("$.loanLimit").value(3))
                .andExpect(jsonPath("$.renewalLimit").value(2))
                .andExpect(jsonPath("$.reservationPickupDays").value(3));
    }

    @Test
    @WithAdmin
    void anAdministratorShouldReadThemToo() throws Exception {
        givenTheConfiguration();

        mockMvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.loanPeriodDays").value(14));
    }

    @Test
    @WithOperator
    void theResponseShouldNotShowThePenaltyPolicyNorTheId() throws Exception {
        givenTheConfiguration();

        var body = mockMvc.perform(get(URL)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("penaltyPolicy").doesNotContain("dailyFineAmount")
                .doesNotContain("fineCap").doesNotContain("suspension");
    }

    @Test
    void withoutAuthenticationShouldAnswer401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenWithoutAKnownProfileShouldAnswer403() throws Exception {
        mockMvc.perform(get(URL).with(jwt())).andExpect(status().isForbidden());
    }

    @Test
    @WithOperator
    void theRulesAreReadOnly() throws Exception {
        mockMvc.perform(patch(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }
}
