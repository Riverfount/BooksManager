package com.riverfount.booksmanager.loan.adapter.in.web;

import com.riverfount.booksmanager.loan.application.port.in.CancelOwnReservationCommand;
import com.riverfount.booksmanager.loan.application.port.in.CancelOwnReservationUseCase;
import com.riverfount.booksmanager.loan.application.port.in.GetOwnReservationQuery;
import com.riverfount.booksmanager.loan.application.port.in.GetOwnReservationUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ListOwnReservationsQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListOwnReservationsUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ListReservationsQuery;
import com.riverfount.booksmanager.loan.application.port.in.ReserveOwnBookCommand;
import com.riverfount.booksmanager.loan.application.port.in.ReserveOwnBookUseCase;
import com.riverfount.booksmanager.loan.domain.ReservationStatus;
import com.riverfount.booksmanager.shared.adapter.in.web.CurrentReader;
import com.riverfount.booksmanager.shared.adapter.in.web.PageResponse;
import com.riverfount.booksmanager.shared.adapter.in.web.ReaderOnly;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * The reservations of the reader (Phase 6, RF16, RF17): reserve a book, see their own reservations and the place in
 * the queue, and cancel one. The reader is always the one of the token ({@link CurrentReader}), never one sent in the
 * request, and the rules are the ones of the desk: only the answers change, to show the reader what is theirs and
 * nothing else (no copy, no other reader).
 */
@ReaderOnly
@RestController
@RequestMapping("/api/v1/me/reservations")
@Tag(name = "Reader reservations", description = "As reservas do próprio leitor: reservar, consultar e cancelar (RF16, RF17).")
class ReaderReservationController {

    private final ReserveOwnBookUseCase reserveUseCase;
    private final GetOwnReservationUseCase getUseCase;
    private final ListOwnReservationsUseCase listUseCase;
    private final CancelOwnReservationUseCase cancelUseCase;

    ReaderReservationController(ReserveOwnBookUseCase reserveUseCase, GetOwnReservationUseCase getUseCase,
            ListOwnReservationsUseCase listUseCase, CancelOwnReservationUseCase cancelUseCase) {
        this.reserveUseCase = reserveUseCase;
        this.getUseCase = getUseCase;
        this.listUseCase = listUseCase;
        this.cancelUseCase = cancelUseCase;
    }

    @PostMapping
    @Operation(summary = "Reservar um livro",
            description = "Reserva, para o leitor logado, um livro que não tem exemplar disponível (RF16): a reserva "
                    + "entra no fim da fila do livro e a resposta traz o lugar na fila. O leitor é o do token; um "
                    + "`readerId` no corpo é ignorado. A data é hoje. Valem as regras da reserva no balcão: leitor "
                    + "ativo e não bloqueado, sem multa pendente, sem suspensão em vigor e sem empréstimo em atraso; "
                    + "o livro não pode ter exemplar disponível, nem já estar emprestado ao leitor ou reservado por "
                    + "ele; o total de reservas ativas respeita o limite da configuração. Só para o perfil de leitor.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reserva registrada, com o lugar na fila"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (livro ausente)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Livro não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409",
                    description = "Outra requisição alterou os mesmos dados ao mesmo tempo; tente novamente",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422",
                    description = "Reserva não permitida; a mensagem traz todos os motivos de uma vez (RN05, RN11, "
                            + "RN12, RN14)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<ReaderReservationResponse> reserve(@CurrentReader Long readerId,
            @Valid @RequestBody ReserveMyBookRequest request) {
        var reservation = reserveUseCase.reserve(new ReserveOwnBookCommand(request.bookId(), readerId));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(reservation.reservation().getId())
                .toUri();
        return ResponseEntity.created(location).body(ReaderReservationResponse.from(reservation));
    }

    @GetMapping
    @Operation(summary = "Listar as minhas reservas",
            description = "Lista as reservas do leitor logado, paginadas (máximo de 100 por página), da mais recente "
                    + "para a mais antiga por padrão, com o filtro opcional `status` (`ACTIVE`, `FULFILLED`, "
                    + "`CANCELLED` ou `EXPIRED`); sem ele, vêm todas, inclusive as encerradas. Cada item traz o lugar "
                    + "na fila (só para a que espera exemplar) e o prazo de retirada (só para a que tem exemplar "
                    + "guardado). Nunca traz reserva de outro leitor. Só para o perfil de leitor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de reservas do leitor"),
            @ApiResponse(responseCode = "400",
                    description = "Paginação, ordenação ou situação inválidas (inclusive tamanho acima de 100)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    PageResponse<ReaderReservationResponse> list(@CurrentReader Long readerId,
            @Parameter(description = "ACTIVE, FULFILLED, CANCELLED ou EXPIRED; sem o parâmetro, todas")
            @RequestParam(required = false) ReservationStatus status,
            @Parameter(description = "Número da página, a partir de 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página, de 1 a 100")
            @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size,
            @Parameter(description = "Ordenação no formato campo[,asc|desc]; campos: id, reservationDate, "
                    + "pickupDeadline", example = "id,desc")
            @RequestParam(defaultValue = ListReservationsQuery.DEFAULT_SORT) String sort) {
        var query = new ListOwnReservationsQuery(readerId, status, PageQuery.of(page, size, sort));
        return PageResponse.from(listUseCase.list(query), ReaderReservationResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar uma reserva minha",
            description = "Devolve a reserva do leitor logado, também a encerrada. Reserva de outro leitor devolve "
                    + "a mesma resposta 404 de uma reserva que não existe. Só para o perfil de leitor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reserva encontrada"),
            @ApiResponse(responseCode = "404", description = "Reserva não encontrada (ou de outro leitor)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ReaderReservationResponse findById(@CurrentReader Long readerId, @PathVariable Long id) {
        return ReaderReservationResponse.from(getUseCase.get(new GetOwnReservationQuery(id, readerId)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancelar uma reserva minha",
            description = "Cancela a reserva do leitor logado (RF17), sem corpo e sem justificativa, tanto a que espera "
                    + "na fila quanto a que guarda um exemplar. Um exemplar que ela guardava passa ao próximo da fila "
                    + "ou volta a disponível (RF18), mas a resposta traz só a reserva cancelada, nunca a de quem "
                    + "recebeu o exemplar. Reserva de outro leitor devolve 404, igual a uma que não existe, antes de "
                    + "qualquer outra verificação. Só para o perfil de leitor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reserva cancelada"),
            @ApiResponse(responseCode = "404", description = "Reserva não encontrada (ou de outro leitor)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409",
                    description = "Outra requisição alterou a mesma reserva ou o mesmo exemplar ao mesmo tempo; "
                            + "tente novamente",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Reserva já encerrada: retirada, expirada ou cancelada",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ReaderReservationResponse cancel(@CurrentReader Long readerId, @PathVariable Long id) {
        return ReaderReservationResponse.from(cancelUseCase.cancel(new CancelOwnReservationCommand(id, readerId)));
    }
}
