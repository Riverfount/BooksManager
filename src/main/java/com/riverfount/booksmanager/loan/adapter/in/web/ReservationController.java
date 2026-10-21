package com.riverfount.booksmanager.loan.adapter.in.web;

import com.riverfount.booksmanager.loan.application.port.in.ReserveBookCommand;
import com.riverfount.booksmanager.loan.application.port.in.ReserveBookUseCase;
import com.riverfount.booksmanager.shared.adapter.in.web.AdminOrOperator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@AdminOrOperator
@RestController
@RequestMapping("/api/v1/reservations")
@Tag(name = "Reservations", description = "Reservas de livros e fila de espera (RF16 a RF19).")
class ReservationController {

    private final ReserveBookUseCase reserveBookUseCase;

    ReservationController(ReserveBookUseCase reserveBookUseCase) {
        this.reserveBookUseCase = reserveBookUseCase;
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
}
