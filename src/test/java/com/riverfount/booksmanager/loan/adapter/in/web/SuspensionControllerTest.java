package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.WithAdmin;
import com.riverfount.booksmanager.WithOperator;
import com.riverfount.booksmanager.loan.application.port.in.ListSuspensionsQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListSuspensionsUseCase;
import com.riverfount.booksmanager.loan.application.port.in.SuspensionDetails;
import com.riverfount.booksmanager.loan.domain.Suspension;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SuspensionController.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class})
class SuspensionControllerTest {

    private static final String URL = "/api/v1/suspensions";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListSuspensionsUseCase listSuspensionsUseCase;

    private static SuspensionDetails running() {
        return new SuspensionDetails(Suspension.reconstitute(6L, 20L, 42L, LocalDate.of(2026, 11, 18),
                LocalDate.of(2026, 11, 25), null, null), true);
    }

    private static SuspensionDetails endedEarly() {
        return new SuspensionDetails(Suspension.reconstitute(7L, 20L, 43L, LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 5), "Pardoned"), false);
    }

    @Test
    @WithOperator
    void shouldListTheSuspensionsWithTheEndDateAndWhetherTheyAreInForce() throws Exception {
        given(listSuspensionsUseCase.list(any()))
                .willReturn(new PageResult<>(List.of(running(), endedEarly()), 0, 20, 2));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(6))
                .andExpect(jsonPath("$.items[0].readerId").value(20))
                .andExpect(jsonPath("$.items[0].loanId").value(42))
                .andExpect(jsonPath("$.items[0].startDate").value("2026-11-18"))
                .andExpect(jsonPath("$.items[0].endDate").value("2026-11-25"))
                .andExpect(jsonPath("$.items[0].releaseDate").value("2026-11-25"))
                .andExpect(jsonPath("$.items[0].inForce").value(true))
                .andExpect(jsonPath("$.items[0].endedEarlyOn").doesNotExist())
                .andExpect(jsonPath("$.items[1].endDate").value("2026-11-20"))
                .andExpect(jsonPath("$.items[1].releaseDate").value("2026-11-05"))
                .andExpect(jsonPath("$.items[1].endedEarlyOn").value("2026-11-05"))
                .andExpect(jsonPath("$.items[1].endedEarlyReason").value("Pardoned"))
                .andExpect(jsonPath("$.items[1].inForce").value(false))
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    @WithAdmin
    void shouldPassTheFiltersAndThePageToTheUseCase() throws Exception {
        given(listSuspensionsUseCase.list(any())).willReturn(new PageResult<>(List.of(), 1, 5, 0));

        mockMvc.perform(get(URL).param("readerId", "20").param("inForce", "true").param("page", "1")
                        .param("size", "5").param("sort", "endDate,asc"))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(ListSuspensionsQuery.class);
        then(listSuspensionsUseCase).should().list(captor.capture());
        var query = captor.getValue();
        assertThat(query.readerId()).isEqualTo(20L);
        assertThat(query.inForce()).isTrue();
        assertThat(query.page().page()).isEqualTo(1);
        assertThat(query.page().size()).isEqualTo(5);
        assertThat(query.page().sortBy()).isEqualTo("endDate");
    }

    @Test
    @WithOperator
    void inForceFalseShouldBeDistinctFromNoFilter() throws Exception {
        given(listSuspensionsUseCase.list(any())).willReturn(new PageResult<>(List.of(), 0, 20, 0));

        mockMvc.perform(get(URL).param("inForce", "false")).andExpect(status().isOk());
        mockMvc.perform(get(URL)).andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(ListSuspensionsQuery.class);
        then(listSuspensionsUseCase).should(org.mockito.Mockito.times(2)).list(captor.capture());
        assertThat(captor.getAllValues().get(0).inForce()).isFalse();
        assertThat(captor.getAllValues().get(1).inForce()).isNull();
        assertThat(captor.getAllValues().get(1).page().sortBy()).isEqualTo("startDate");
    }

    @Test
    @WithOperator
    void aPageSizeAbove100ShouldAnswer400() throws Exception {
        mockMvc.perform(get(URL).param("size", "101")).andExpect(status().isBadRequest());

        then(listSuspensionsUseCase).should(never()).list(any());
    }

    @Test
    @WithOperator
    void aBadFilterOrSortFieldShouldAnswer400() throws Exception {
        mockMvc.perform(get(URL).param("inForce", "maybe")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("sort", "readerId")).andExpect(status().isBadRequest());

        then(listSuspensionsUseCase).should(never()).list(any());
    }

    @Test
    void withoutAuthenticationShouldAnswer401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
