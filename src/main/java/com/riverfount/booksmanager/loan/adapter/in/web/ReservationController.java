package com.riverfount.booksmanager.loan.adapter.in.web;

import com.riverfount.booksmanager.loan.application.port.in.CancelReservationCommand;
import com.riverfount.booksmanager.loan.application.port.in.CancelReservationUseCase;
import com.riverfount.booksmanager.loan.application.port.in.GetReservationUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ListReservationsQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListReservationsUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ReserveBookCommand;
import com.riverfount.booksmanager.loan.application.port.in.ReserveBookUseCase;
import com.riverfount.booksmanager.loan.domain.ReservationStatus;
import com.riverfount.booksmanager.shared.adapter.in.web.AdminOrOperator;
import com.riverfount.booksmanager.shared.adapter.in.web.PageResponse;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
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

@AdminOrOperator
@RestController
@RequestMapping("/api/v1/reservations")
@Tag(name = "Reservations", description = "Reservas de livros e fila de espera (RF16 a RF19).")
class ReservationController {

    private final ReserveBookUseCase reserveBookUseCase;
    private final GetReservationUseCase getReservationUseCase;
    private final ListReservationsUseCase listReservationsUseCase;
    private final CancelReservationUseCase cancelReservationUseCase;

    ReservationController(ReserveBookUseCase reserveBookUseCase, GetReservationUseCase getReservationUseCase,
            ListReservationsUseCase listReservationsUseCase, CancelReservationUseCase cancelReservationUseCase) {
        this.reserveBookUseCase = reserveBookUseCase;
        this.getReservationUseCase = getReservationUseCase;
        this.listReservationsUseCase = listReservationsUseCase;
        this.cancelReservationUseCase = cancelReservationUseCase;
    }

    @PostMapping
    @Operation(summary = "Reservar livro",
            description = "Reserva um livro que não tem exemplar disponível para um leitor (RF16): a reserva entra "
                    + "no fim da fila do livro, e a resposta traz o lugar na fila. A data da reserva é hoje: nenhuma "
                    + "data é aceita no corpo. Só reserva quem poderia emprestar, exceto pela disponibilidade e pelo "
                    + "limite de empréstimos: leitor ativo e não bloqueado, sem multa pendente, sem suspensão em "
                    + "vigor e sem empréstimo em atraso. O livro não pode ter exemplar disponível (é caso de "
                    + "emprestar), nem já estar emprestado ao leitor ou reservado por ele. O total de reservas ativas "
                    + "do leitor tem como limite o de empréstimos da configuração, contado à parte dos empréstimos.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reserva registrada, com o lugar na fila"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (livro ou leitor ausente)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Livro ou leitor não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409",
                    description = "Outra requisição alterou os mesmos dados ao mesmo tempo; tente novamente",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422",
                    description = "Reserva não permitida; a mensagem traz todos os motivos de uma vez: leitor "
                            + "inativo ou bloqueado (RN11), livro com exemplar disponível, já emprestado ao leitor "
                            + "ou já reservado por ele, limite de reservas ativas (RN12), multa pendente, suspensão "
                            + "ou empréstimo em atraso (RN05)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReserveBookRequest request) {
        var result = reserveBookUseCase.reserve(new ReserveBookCommand(request.bookId(), request.readerId()));
        var reservation = result.reservation();
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(reservation.getId())
                .toUri();
        return ResponseEntity.created(location).body(ReservationResponse.from(reservation, result.queuePosition()));
    }

    @GetMapping
    @Operation(summary = "Listar reservas",
            description = "Lista as reservas de forma paginada (máximo de 100 por página), por leitor, por livro e "
                    + "por situação: `ACTIVE`, `FULFILLED` (retirada), `CANCELLED` ou `EXPIRED`. Sem filtro de "
                    + "situação, vêm todas, inclusive as encerradas. Filtrar por livro e `status=ACTIVE` mostra a "
                    + "fila do livro. Por padrão, da mais recente para a mais antiga. Cada item traz o nome do "
                    + "leitor, o título do livro, o lugar na fila (só para a reserva que espera exemplar, na ordem "
                    + "de chegada) e, para a que segura um exemplar, o código de patrimônio e o prazo de retirada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de reservas"),
            @ApiResponse(responseCode = "400",
                    description = "Paginação, ordenação ou filtro inválidos (inclusive tamanho acima de 100 e "
                            + "situação desconhecida)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    PageResponse<ReservationDetailsResponse> list(
            @Parameter(description = "Só as reservas deste leitor", example = "20")
            @RequestParam(required = false) Long readerId,
            @Parameter(description = "Só as reservas deste livro", example = "30")
            @RequestParam(required = false) Long bookId,
            @Parameter(description = "ACTIVE, FULFILLED, CANCELLED ou EXPIRED; sem o parâmetro, todas")
            @RequestParam(required = false) ReservationStatus status,
            @Parameter(description = "Número da página, a partir de 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página, de 1 a 100")
            @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size,
            @Parameter(description = "Ordenação no formato campo[,asc|desc]; campos: id, reservationDate, "
                    + "pickupDeadline", example = "id,desc")
            @RequestParam(defaultValue = ListReservationsQuery.DEFAULT_SORT) String sort) {
        var query = new ListReservationsQuery(readerId, bookId, status, PageQuery.of(page, size, sort));
        return PageResponse.from(listReservationsUseCase.list(query), ReservationDetailsResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar reserva por id",
            description = "Devolve a reserva com o nome do leitor, o título do livro, a situação, o lugar na fila "
                    + "(se espera exemplar) e, se segura um, o exemplar e o prazo de retirada. Vale também para "
                    + "reserva encerrada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reserva encontrada"),
            @ApiResponse(responseCode = "400", description = "Identificador inválido",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Reserva não encontrada",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ReservationDetailsResponse findById(@PathVariable Long id) {
        return getReservationUseCase.findById(id)
                .map(ReservationDetailsResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("reserva não encontrada: " + id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancelar reserva",
            description = "Cancela a reserva (RF17), sem corpo e sem justificativa; o leitor não acessa o sistema, "
                    + "então o cancelamento é feito pelo operador ou pelo administrador, em nome dele. Vale para a "
                    + "reserva que espera na fila e para a que guarda um exemplar. Se guardava um exemplar, ele "
                    + "passa ao próximo da fila (a resposta traz a reserva atendida) ou volta a disponível, se "
                    + "ninguém espera (RF18). Tudo ou nada: se qualquer passo falhar, nada é gravado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Reserva cancelada e, se havia, a reserva que recebeu o exemplar liberado"),
            @ApiResponse(responseCode = "400", description = "Identificador inválido",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Reserva não encontrada",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409",
                    description = "Outra requisição alterou a mesma reserva ou o mesmo exemplar ao mesmo tempo; "
                            + "tente novamente",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422",
                    description = "Reserva já encerrada: retirada, expirada ou cancelada",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    CancelReservationResponse cancel(@PathVariable Long id) {
        return CancelReservationResponse.from(cancelReservationUseCase.cancel(new CancelReservationCommand(id)));
    }
}
