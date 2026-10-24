package com.riverfount.booksmanager.catalog.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.WithAdmin;
import com.riverfount.booksmanager.WithOperator;
import com.riverfount.booksmanager.catalog.application.port.in.CollectionStatus;
import com.riverfount.booksmanager.catalog.application.port.in.GetCollectionStatusUseCase;
import com.riverfount.booksmanager.catalog.domain.CopyStatus;
import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CollectionReportController.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class,
        CollectionReportCsvControllerTest.FixedClock.class})
class CollectionReportCsvControllerTest {

    private static final String URL = "/api/v1/reports/collection-status/csv";

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-24T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetCollectionStatusUseCase getCollectionStatusUseCase;

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
    void shouldExportOneLinePerStatusWithTheTotalAndTheInactiveApart() throws Exception {
        collectionIs(90, 20, 4, 3, 3, 5);

        var result = mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"collection-status-2026-10-24.csv\""))
                .andReturn();

        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("﻿Situação;Exemplares\r\n"
                        + "Total de exemplares ativos;120\r\n"
                        + "Disponível;90\r\n"
                        + "Emprestado;20\r\n"
                        + "Reservado;4\r\n"
                        + "Danificado;3\r\n"
                        + "Extraviado;3\r\n"
                        + "Exemplares inativos;5\r\n");
    }

    @Test
    @WithAdmin
    void anAdministratorShouldAlsoExportIt() throws Exception {
        collectionIs(0, 0, 0, 0, 0, 0);

        mockMvc.perform(get(URL)).andExpect(status().isOk());
    }

    @Test
    void anAnonymousRequestShouldAnswer401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
