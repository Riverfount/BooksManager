package com.riverfount.booksmanager.loan.adapter.in.web;

import com.riverfount.booksmanager.loan.application.port.in.RegisterLoanCommand;
import com.riverfount.booksmanager.loan.application.port.in.RegisterLoanUseCase;
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
@RequestMapping("/api/v1/loans")
@Tag(name = "Loans", description = "Empréstimos de exemplares a leitores (RF11).")
class LoanController {

    private final RegisterLoanUseCase registerLoanUseCase;

    LoanController(RegisterLoanUseCase registerLoanUseCase) {
        this.registerLoanUseCase = registerLoanUseCase;
    }

    @PostMapping
    @Operation(summary = "Registrar empréstimo",
            description = "Empresta um exemplar a um leitor (HU07). A retirada é hoje e a data prevista de devolução "
                    + "vem do prazo da configuração da biblioteca: nenhuma data é aceita no corpo. O exemplar passa "
                    + "a emprestado na mesma operação; se qualquer passo falhar, nada é gravado.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Empréstimo registrado"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (exemplar ou leitor ausente)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Exemplar ou leitor não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409",
                    description = "Outra requisição alterou o mesmo exemplar ao mesmo tempo; tente novamente",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422",
                    description = "Empréstimo não permitido; a mensagem traz todos os motivos de uma vez: exemplar "
                            + "indisponível (RN03), limite de empréstimos simultâneos (RN04), multa pendente, "
                            + "suspensão ou empréstimo em atraso (RN05), leitor inativo ou bloqueado (RN11)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<LoanResponse> register(@Valid @RequestBody RegisterLoanRequest request) {
        var loan = registerLoanUseCase.register(new RegisterLoanCommand(request.copyId(), request.readerId()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(loan.getId())
                .toUri();
        return ResponseEntity.created(location).body(LoanResponse.from(loan));
    }
}
