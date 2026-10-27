package com.riverfount.booksmanager.loan.adapter.in.web;

import com.riverfount.booksmanager.loan.application.port.in.GetOwnStandingQuery;
import com.riverfount.booksmanager.loan.application.port.in.GetOwnStandingUseCase;
import com.riverfount.booksmanager.shared.adapter.in.web.CurrentReader;
import com.riverfount.booksmanager.shared.adapter.in.web.ReaderOnly;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * How the reader stands (Phase 6, RF10, RF20, RF21): one resource, always the reader of the token ({@link
 * CurrentReader}), never one sent in the request. A blocked, suspended or fined reader signs in and reads like any
 * other; this tells them why they cannot borrow, reserve or renew before they try.
 */
@ReaderOnly
@RestController
@RequestMapping("/api/v1/me/standing")
@Tag(name = "Reader standing", description = "A situação do próprio leitor: se pode pegar emprestado e o que o impede "
        + "(RF10, RF20, RF21).")
class ReaderStandingController {

    private final GetOwnStandingUseCase getUseCase;

    ReaderStandingController(GetOwnStandingUseCase getUseCase) {
        this.getUseCase = getUseCase;
    }

    @GetMapping
    @Operation(summary = "Consultar a minha situação",
            description = "Devolve a situação do leitor logado hoje: se poderia pegar um livro emprestado (`canBorrow`) "
                    + "e, se não, todos os motivos de uma vez, os mesmos da recusa de um empréstimo no balcão; se está "
                    + "bloqueado (sem o motivo, que é decisão do operador); o total das multas pendentes; a "
                    + "suspensão em vigor, com o início e o dia em que volta a poder emprestar; e quantos empréstimos "
                    + "e reservas ativas tem, com os limites da biblioteca. Só traz o que é do próprio leitor. "
                    + "Bloqueado, suspenso ou com multa pendente também consulta normalmente. Só para o perfil de "
                    + "leitor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A situação do leitor")
    })
    ReaderStandingResponse get(@CurrentReader Long readerId) {
        return ReaderStandingResponse.from(getUseCase.get(new GetOwnStandingQuery(readerId)));
    }
}
