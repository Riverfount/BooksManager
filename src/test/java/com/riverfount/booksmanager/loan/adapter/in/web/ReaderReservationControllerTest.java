package com.riverfount.booksmanager.loan.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.riverfount.booksmanager.WithAdmin;
import com.riverfount.booksmanager.WithOperator;
import com.riverfount.booksmanager.catalog.application.port.in.CopySummary;
import com.riverfount.booksmanager.loan.application.port.in.CancelOwnReservationCommand;
import com.riverfount.booksmanager.loan.application.port.in.CancelOwnReservationUseCase;
import com.riverfount.booksmanager.loan.application.port.in.GetOwnReservationQuery;
import com.riverfount.booksmanager.loan.application.port.in.GetOwnReservationUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ListOwnReservationsQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListOwnReservationsUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ReservationDetails;
import com.riverfount.booksmanager.loan.application.port.in.ReserveOwnBookCommand;
import com.riverfount.booksmanager.loan.application.port.in.ReserveOwnBookUseCase;
import com.riverfount.booksmanager.loan.domain.Reservation;
import com.riverfount.booksmanager.loan.domain.ReservationStatus;
import com.riverfount.booksmanager.security.configuration.MethodSecurityConfiguration;
import com.riverfount.booksmanager.shared.adapter.in.web.GlobalExceptionHandler;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The reservations of the reader (Phase 6, RF16, RF17): the reader is the one of the token, what comes back is only
 * what is theirs (no copy, no other reader), and the administrative profiles are kept out.
 */
@WebMvcTest(ReaderReservationController.class)
@Import({GlobalExceptionHandler.class, MethodSecurityConfiguration.class})
class ReaderReservationControllerTest {

    private static final long ME = 42L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReserveOwnBookUseCase reserveUseCase;

    @MockitoBean
    private GetOwnReservationUseCase getUseCase;

    @MockitoBean
    private ListOwnReservationsUseCase listUseCase;

    @MockitoBean
    private CancelOwnReservationUseCase cancelUseCase;

    private static RequestPostProcessor reader() {
        return jwt().jwt(token -> token.subject("5").claim("readerId", ME))
                .authorities(new SimpleGrantedAuthority("ROLE_READER"));
    }

    private static ReservationDetails waiting(long id, long queuePosition) {
        return new ReservationDetails(Reservation.reconstitute(id, 30L, ME, LocalDate.of(2026, 11, 10),
                ReservationStatus.ACTIVE, null, null), queuePosition, "Maria Silva", "Dom Casmurro", null);
    }

    private static ReservationDetails holding(long id) {
        return new ReservationDetails(Reservation.reconstitute(id, 30L, ME, LocalDate.of(2026, 11, 10),
                ReservationStatus.ACTIVE, 7L, LocalDate.of(2026, 11, 13)), null, "Maria Silva", "Dom Casmurro",
                new CopySummary(7L, "PAT-0007", 30L, "Dom Casmurro"));
    }

    private static ReservationDetails cancelled(long id) {
        return new ReservationDetails(Reservation.reconstitute(id, 30L, ME, LocalDate.of(2026, 11, 10),
                ReservationStatus.CANCELLED, null, null), null, "Maria Silva", "Dom Casmurro", null);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder reserve(String json) {
        return post("/api/v1/me/reservations").with(csrf()).with(reader()).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    // reserving

    @Test
    void shouldReserveForTheReaderOfTheTokenAndAnswer201WithTheLocation() throws Exception {
        given(reserveUseCase.reserve(any())).willReturn(waiting(5L, 3L));

        mockMvc.perform(reserve("{\"bookId\":30}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/me/reservations/5")))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.bookId").value(30))
                .andExpect(jsonPath("$.bookTitle").value("Dom Casmurro"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.queuePosition").value(3));

        then(reserveUseCase).should().reserve(new ReserveOwnBookCommand(30L, ME));
    }

    @Test
    void shouldIgnoreAReaderSentInTheBody() throws Exception {
        given(reserveUseCase.reserve(any())).willReturn(waiting(5L, 1L));

        mockMvc.perform(reserve("{\"bookId\":30,\"readerId\":99}")).andExpect(status().isCreated());

        then(reserveUseCase).should().reserve(new ReserveOwnBookCommand(30L, ME));
    }

    @Test
    void shouldRefuseABodyWithoutTheBookWith400() throws Exception {
        mockMvc.perform(reserve("{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isNotEmpty());
        mockMvc.perform(reserve("{\"readerId\":99}")).andExpect(status().isBadRequest());
        verifyNoInteractions(reserveUseCase);
    }

    @Test
    void shouldAnswer422WithEveryReasonAndTheRulesOfTheDesk() throws Exception {
        given(reserveUseCase.reserve(any())).willThrow(
                new BusinessRuleException("multa pendente; limite de reservas atingido"));

        mockMvc.perform(reserve("{\"bookId\":30}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("multa pendente; limite de reservas atingido"));
    }

    @Test
    void shouldAnswer404ForAnUnknownBook() throws Exception {
        given(reserveUseCase.reserve(any())).willThrow(new ResourceNotFoundException("livro não encontrado: 30"));

        mockMvc.perform(reserve("{\"bookId\":30}")).andExpect(status().isNotFound());
    }

    @Test
    void theResponseShouldHaveNothingOfTheCopyNorOfTheReader() throws Exception {
        given(reserveUseCase.reserve(any())).willReturn(holding(5L));

        var body = mockMvc.perform(reserve("{\"bookId\":30}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.pickupDeadline").value("2026-11-13"))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> reservation = JsonPath.read(body, "$");
        assertThat(reservation.keySet()).containsExactlyInAnyOrder("id", "bookId", "bookTitle", "reservationDate",
                "pickupDeadline", "status", "queuePosition");
        assertThat(body).doesNotContain("PAT-0007").doesNotContain("Maria Silva");
    }

    @Test
    void aClosedReservationShouldNeverShowAPickupDeadline() throws Exception {
        // it keeps the old date in the database, but "pick it up by" means nothing once it is closed
        var closed = new ReservationDetails(Reservation.reconstitute(5L, 30L, ME, LocalDate.of(2026, 11, 10),
                ReservationStatus.CANCELLED, 7L, LocalDate.of(2026, 11, 13)), null, "Maria Silva", "Dom Casmurro",
                null);
        given(getUseCase.get(any())).willReturn(closed);

        mockMvc.perform(get("/api/v1/me/reservations/5").with(reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.pickupDeadline").doesNotExist());
    }

    // listing and looking

    @Test
    void shouldListTheReservationsOfTheReaderOfTheTokenOnly() throws Exception {
        given(listUseCase.list(any())).willReturn(new PageResult<>(List.of(waiting(5L, 2L)), 0, 20, 1));

        mockMvc.perform(get("/api/v1/me/reservations").with(reader()).param("status", "ACTIVE")
                        .param("readerId", "99").param("size", "10").param("sort", "reservationDate,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(5))
                .andExpect(jsonPath("$.items[0].queuePosition").value(2))
                .andExpect(jsonPath("$.totalItems").value(1));

        var captor = ArgumentCaptor.forClass(ListOwnReservationsQuery.class);
        then(listUseCase).should().list(captor.capture());
        assertThat(captor.getValue().readerId()).isEqualTo(ME);
        assertThat(captor.getValue().status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(captor.getValue().page().size()).isEqualTo(10);
    }

    @Test
    void shouldRefuseAPageBiggerThan100AnUnknownSortAndAnUnknownStatus() throws Exception {
        mockMvc.perform(get("/api/v1/me/reservations").with(reader()).param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/me/reservations").with(reader()).param("status", "NOPE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldShowAReservationOfTheReader() throws Exception {
        given(getUseCase.get(new GetOwnReservationQuery(5L, ME))).willReturn(waiting(5L, 1L));

        mockMvc.perform(get("/api/v1/me/reservations/5").with(reader()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.queuePosition").value(1));
    }

    @Test
    void aReservationOfAnotherReaderShouldBeA404() throws Exception {
        given(getUseCase.get(any())).willThrow(new ResourceNotFoundException("reserva não encontrada: 9"));

        mockMvc.perform(get("/api/v1/me/reservations/9").with(reader()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    // cancelling

    @Test
    void shouldCancelAReservationOfTheReaderAndAnswerOnlyWithIt() throws Exception {
        given(cancelUseCase.cancel(any())).willReturn(cancelled(5L));

        var body = mockMvc.perform(post("/api/v1/me/reservations/5/cancel").with(csrf()).with(reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.served").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("served");
        then(cancelUseCase).should().cancel(new CancelOwnReservationCommand(5L, ME));
    }

    @Test
    void shouldAnswer404WhenCancellingWhatIsNotTheirsAnd422WhenItIsAlreadyClosed() throws Exception {
        given(cancelUseCase.cancel(new CancelOwnReservationCommand(9L, ME)))
                .willThrow(new ResourceNotFoundException("reserva não encontrada: 9"));
        given(cancelUseCase.cancel(new CancelOwnReservationCommand(6L, ME)))
                .willThrow(new BusinessRuleException("a reserva já está encerrada"));

        mockMvc.perform(post("/api/v1/me/reservations/9/cancel").with(csrf()).with(reader())).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/me/reservations/6/cancel").with(csrf()).with(reader()))
                .andExpect(status().isUnprocessableContent());
    }

    // who may call

    @Test
    @WithAdmin
    void anAdministratorMayNotUseTheReservationsOfTheReader() throws Exception {
        mockMvc.perform(post("/api/v1/me/reservations").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":30}")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/me/reservations")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/me/reservations/5")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/me/reservations/5/cancel").with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(reserveUseCase, listUseCase, getUseCase, cancelUseCase);
    }

    @Test
    @WithOperator
    void anOperatorMayNotUseTheReservationsOfTheReader() throws Exception {
        mockMvc.perform(get("/api/v1/me/reservations")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/me/reservations/5/cancel").with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(listUseCase, cancelUseCase);
    }

    @Test
    void anonymousIs401() throws Exception {
        mockMvc.perform(get("/api/v1/me/reservations")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/me/reservations").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":30}")).andExpect(status().isUnauthorized());
    }
}
