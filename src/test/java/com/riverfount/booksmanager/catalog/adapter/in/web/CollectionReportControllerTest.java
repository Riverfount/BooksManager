package com.riverfount.booksmanager.catalog.adapter.in.web;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.WithAdmin;
import com.riverfount.booksmanager.WithOperator;
import com.riverfount.booksmanager.catalog.application.port.in.CollectionStatus;
import com.riverfount.booksmanager.catalog.application.port.in.GetCollectionStatusUseCase;
import com.riverfount.booksmanager.catalog.domain.CopyStatus;
import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CollectionReportController.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class})
class CollectionReportControllerTest {

    private static final String URL = "/api/v1/reports/collection-status";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetCollectionStatusUseCase getCollectionStatusUseCase;

    @MockitoBean
    private java.time.Clock clock;

    private void collectionIs(long available, long borrowed, long reserved, long damaged, long lost, long inactive) {
        var byStatus = new EnumMap<CopyStatus, Long>(CopyStatus.class);
        byStatus.put(CopyStatus.AVAILABLE, available);
        byStatus.put(CopyStatus.BORROWED, borrowed);
        byStatus.put(CopyStatus.RESERVED, reserved);
        byStatus.put(CopyStatus.DAMAGED, damaged);
        byStatus.put(CopyStatus.LOST, lost);
        given(getCollectionStatusUseCase.get()).willReturn(
                new CollectionStatus(available + borrowed + reserved + damaged + lost, byStatus, inactive));
    }

    @Test
    @WithOperator
    void shouldShowTheTotalTheCountByStatusAndTheInactiveApart() throws Exception {
        collectionIs(90, 20, 4, 3, 3, 5);

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCopies").value(120))
                .andExpect(jsonPath("$.activeByStatus.AVAILABLE").value(90))
                .andExpect(jsonPath("$.activeByStatus.BORROWED").value(20))
                .andExpect(jsonPath("$.activeByStatus.RESERVED").value(4))
                .andExpect(jsonPath("$.activeByStatus.DAMAGED").value(3))
                .andExpect(jsonPath("$.activeByStatus.LOST").value(3))
                .andExpect(jsonPath("$.inactiveCopies").value(5));
    }

    @Test
    @WithAdmin
    void anAdministratorShouldAlsoReadItAndAStatusWithNoCopiesShouldBeZero() throws Exception {
        collectionIs(2, 0, 0, 0, 0, 0);

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCopies").value(2))
                .andExpect(jsonPath("$.activeByStatus.BORROWED").value(0))
                .andExpect(jsonPath("$.activeByStatus.LOST").value(0))
                .andExpect(jsonPath("$.inactiveCopies").value(0));
    }

    @Test
    void withoutLoggingInTheAnswerShouldBe401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
