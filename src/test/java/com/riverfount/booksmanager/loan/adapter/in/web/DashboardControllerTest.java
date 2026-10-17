package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.WithAdmin;
import com.riverfount.booksmanager.WithOperator;
import com.riverfount.booksmanager.catalog.application.port.in.CategoryCopies;
import com.riverfount.booksmanager.loan.application.port.in.Dashboard;
import com.riverfount.booksmanager.loan.application.port.in.GetDashboardUseCase;
import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class})
class DashboardControllerTest {

    private static final String URL = "/api/v1/reports/dashboard";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetDashboardUseCase getDashboardUseCase;

    private void givenTheDashboard() {
        given(getDashboardUseCase.get()).willReturn(new Dashboard(120, 18, 4, 3, List.of(
                new CategoryCopies(1L, "Ficção", 30, 80), new CategoryCopies(2L, "Poesia", 0, 0))));
    }

    @Test
    @WithOperator
    void anOperatorShouldReadTheDashboard() throws Exception {
        givenTheDashboard();

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCopies").value(120))
                .andExpect(jsonPath("$.activeLoans").value(18))
                .andExpect(jsonPath("$.overdueLoans").value(4))
                .andExpect(jsonPath("$.suspendedReaders").value(3))
                .andExpect(jsonPath("$.copiesByCategory.length()").value(2))
                .andExpect(jsonPath("$.copiesByCategory[0].categoryId").value(1))
                .andExpect(jsonPath("$.copiesByCategory[0].categoryName").value("Ficção"))
                .andExpect(jsonPath("$.copiesByCategory[0].books").value(30))
                .andExpect(jsonPath("$.copiesByCategory[0].copies").value(80))
                .andExpect(jsonPath("$.copiesByCategory[1].copies").value(0));
    }

    @Test
    @WithAdmin
    void anAdministratorShouldReadItToo() throws Exception {
        givenTheDashboard();

        mockMvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.activeLoans").value(18));
    }

    @Test
    void withoutAuthenticationShouldAnswer401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenWithoutAKnownProfileShouldAnswer403() throws Exception {
        mockMvc.perform(get(URL).with(jwt())).andExpect(status().isForbidden());
    }
}
