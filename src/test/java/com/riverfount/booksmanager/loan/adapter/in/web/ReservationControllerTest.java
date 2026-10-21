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
import com.riverfount.booksmanager.loan.application.port.in.ReserveBookCommand;
import com.riverfount.booksmanager.loan.application.port.in.ReserveBookResult;
import com.riverfount.booksmanager.loan.application.port.in.ReserveBookUseCase;
import com.riverfount.booksmanager.loan.domain.Reservation;
import com.riverfount.booksmanager.loan.domain.ReservationStatus;
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

@WebMvcTest(ReservationController.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class})
class ReservationControllerTest {

    private static final String URL = "/api/v1/reservations";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReserveBookUseCase reserveBookUseCase;

    private static ReserveBookResult reserved() {
        var reservation = Reservation.reconstitute(42L, 30L, 20L, LocalDate.of(2026, 11, 10),
                ReservationStatus.ACTIVE, null, null);
        return new ReserveBookResult(reservation, 3L);
    }

    private ResultActions reserve(String json) throws Exception {
        return mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    @WithOperator
    void shouldReserveTheBookAndReturn201WithLocationAndThePositionInTheQueue() throws Exception {
        given(reserveBookUseCase.reserve(any())).willReturn(reserved());

        reserve("{\"bookId\":30,\"readerId\":20}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/reservations/42")))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.bookId").value(30))
                .andExpect(jsonPath("$.readerId").value(20))
                .andExpect(jsonPath("$.reservationDate").value("2026-11-10"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.queuePosition").value(3))
                .andExpect(jsonPath("$.copyId").doesNotExist())
                .andExpect(jsonPath("$.pickupDeadline").doesNotExist());
    }

    @Test
    @WithAdmin
    void anAdministratorShouldAlsoReserve() throws Exception {
        given(reserveBookUseCase.reserve(any())).willReturn(reserved());

        reserve("{\"bookId\":30,\"readerId\":20}").andExpect(status().isCreated());
    }

    @Test
    @WithOperator
    void shouldPassTheIdsToTheUseCaseAndIgnoreWhatTheClientCannotChoose() throws Exception {
        given(reserveBookUseCase.reserve(any())).willReturn(reserved());

        reserve("{\"bookId\":30,\"readerId\":20,\"reservationDate\":\"2030-01-01\",\"status\":\"FULFILLED\"}")
                .andExpect(status().isCreated());

        var captor = ArgumentCaptor.forClass(ReserveBookCommand.class);
        then(reserveBookUseCase).should().reserve(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ReserveBookCommand(30L, 20L));
    }

    @Test
    @WithOperator
    void shouldReturn400WhenTheBookIsMissing() throws Exception {
        reserve("{\"readerId\":20}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));

        then(reserveBookUseCase).should(never()).reserve(any());
    }

    @Test
    @WithOperator
    void shouldReturn400WhenTheReaderIsMissing() throws Exception {
        reserve("{\"bookId\":30}").andExpect(status().isBadRequest());

        then(reserveBookUseCase).should(never()).reserve(any());
    }

    @Test
    @WithOperator
    void shouldReturn400ForAnIdThatIsNotANumber() throws Exception {
        reserve("{\"bookId\":\"abc\",\"readerId\":20}").andExpect(status().isBadRequest());
    }

    @Test
    @WithOperator
    void shouldReturn422WithAllTheReasonsWhenTheReservationIsRefused() throws Exception {
        given(reserveBookUseCase.reserve(any())).willThrow(new BusinessRuleException(
                "A reserva não pode ser feita: o leitor tem multa pendente; o leitor está suspenso."));

        reserve("{\"bookId\":30,\"readerId\":20}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value(
                        "A reserva não pode ser feita: o leitor tem multa pendente; o leitor está suspenso."));
    }

    @Test
    @WithOperator
    void shouldReturn404WhenTheBookOrTheReaderDoesNotExist() throws Exception {
        given(reserveBookUseCase.reserve(any())).willThrow(new ResourceNotFoundException("livro não encontrado: 30"));

        reserve("{\"bookId\":30,\"readerId\":20}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("livro não encontrado: 30"));
    }

    @Test
    @WithOperator
    void shouldReturn409WhenAnotherRequestChangedTheSameDataFirst() throws Exception {
        given(reserveBookUseCase.reserve(any())).willThrow(new OptimisticLockingFailureException("stale"));

        reserve("{\"bookId\":30,\"readerId\":20}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value(containsString("tente novamente")));
    }

    @Test
    void shouldReturn401WithoutAuthentication() throws Exception {
        reserve("{\"bookId\":30,\"readerId\":20}").andExpect(status().isUnauthorized());

        then(reserveBookUseCase).should(never()).reserve(any());
    }
}
