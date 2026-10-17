package com.riverfount.booksmanager.loan.adapter.in.web;

import com.riverfount.booksmanager.loan.application.port.in.GetLoanUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ListLoansQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListLoansUseCase;
import com.riverfount.booksmanager.loan.application.port.in.LoanStatus;
import com.riverfount.booksmanager.loan.application.port.in.RegisterLoanCommand;
import com.riverfount.booksmanager.loan.application.port.in.RegisterLoanUseCase;
import com.riverfount.booksmanager.loan.application.port.in.RenewLoanCommand;
import com.riverfount.booksmanager.loan.application.port.in.RenewLoanUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ReturnLoanCommand;
import com.riverfount.booksmanager.loan.application.port.in.ReturnLoanUseCase;
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
@RequestMapping("/api/v1/loans")
@Tag(name = "Loans", description = "Empréstimos, devoluções, renovações e consultas de exemplares (RF11 a RF14).")
class LoanController {

    private final RegisterLoanUseCase registerLoanUseCase;
    private final ReturnLoanUseCase returnLoanUseCase;
    private final RenewLoanUseCase renewLoanUseCase;
    private final GetLoanUseCase getLoanUseCase;
    private final ListLoansUseCase listLoansUseCase;

    LoanController(RegisterLoanUseCase registerLoanUseCase, ReturnLoanUseCase returnLoanUseCase,
            RenewLoanUseCase renewLoanUseCase, GetLoanUseCase getLoanUseCase, ListLoansUseCase listLoansUseCase) {
        this.registerLoanUseCase = registerLoanUseCase;
        this.returnLoanUseCase = returnLoanUseCase;
        this.renewLoanUseCase = renewLoanUseCase;
        this.getLoanUseCase = getLoanUseCase;
        this.listLoansUseCase = listLoansUseCase;
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

    @PostMapping("/{id}/return")
    @Operation(summary = "Registrar devolução",
            description = "Devolve o exemplar (HU08): encerra o empréstimo com a data de hoje, deixa o exemplar "
                    + "disponível de novo (um exemplar danificado ou extraviado continua como está) e, se a "
                    + "devolução for depois da data prevista, aplica a penalidade da política vigente na "
                    + "configuração: multa, suspensão, as duas ou nada. A penalidade é calculada agora e guardada; "
                    + "mudar a política depois não recalcula devoluções já registradas. Tudo ou nada: se qualquer "
                    + "passo falhar, nada é gravado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Devolução registrada: o empréstimo encerrado, os dias de atraso e, se houve, a "
                            + "multa e a suspensão geradas"),
            @ApiResponse(responseCode = "400", description = "Identificador inválido",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Empréstimo não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409",
                    description = "Outra requisição alterou o mesmo empréstimo ou exemplar ao mesmo tempo; tente "
                            + "novamente",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422",
                    description = "Empréstimo já encerrado (RN09) ou devolução anterior à retirada",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ReturnLoanResponse returnLoan(@PathVariable Long id) {
        return ReturnLoanResponse.from(returnLoanUseCase.returnLoan(new ReturnLoanCommand(id)));
    }

    @PostMapping("/{id}/renew")
    @Operation(summary = "Renovar empréstimo",
            description = "Renova um empréstimo ativo (HU09): a data prevista de devolução avança o prazo da "
                    + "configuração da biblioteca, contado a partir da data prevista atual, e a renovação é "
                    + "contada. O limite de renovações por empréstimo também vem da configuração. Não renova "
                    + "empréstimo encerrado, em atraso ou que já usou todas as renovações. Sem corpo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Empréstimo renovado, com a nova data prevista"),
            @ApiResponse(responseCode = "400", description = "Identificador inválido",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Empréstimo não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409",
                    description = "Outra requisição alterou o mesmo empréstimo ao mesmo tempo; tente novamente",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422",
                    description = "Renovação não permitida (a mensagem diz o motivo): empréstimo encerrado (RN09), "
                            + "em atraso ou com o limite de renovações atingido (RN07)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    LoanResponse renew(@PathVariable Long id) {
        return LoanResponse.from(renewLoanUseCase.renew(new RenewLoanCommand(id)));
    }

    @GetMapping
    @Operation(summary = "Listar empréstimos",
            description = "Lista os empréstimos de forma paginada (máximo de 100 por página), por leitor, por "
                    + "exemplar e por situação: `ACTIVE` (ainda não devolvidos) ou `RETURNED` (encerrados, o "
                    + "histórico). Sem filtro de situação, vêm todos. Por padrão, do mais recente para o mais "
                    + "antigo. Cada item traz as datas de retirada, prevista e de devolução e, para o empréstimo "
                    + "devolvido, os dias de atraso e a multa e a suspensão geradas, quando houver.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de empréstimos"),
            @ApiResponse(responseCode = "400",
                    description = "Paginação, ordenação ou filtro inválidos (inclusive tamanho acima de 100 e "
                            + "situação desconhecida)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    PageResponse<LoanDetailsResponse> list(
            @Parameter(description = "Só os empréstimos deste leitor", example = "20")
            @RequestParam(required = false) Long readerId,
            @Parameter(description = "Só os empréstimos deste exemplar, inclusive os já encerrados", example = "7")
            @RequestParam(required = false) Long copyId,
            @Parameter(description = "ACTIVE (ainda não devolvidos) ou RETURNED (encerrados); sem o parâmetro, ambos")
            @RequestParam(required = false) LoanStatus status,
            @Parameter(description = "Número da página, a partir de 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página, de 1 a 100")
            @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size,
            @Parameter(description = "Ordenação no formato campo[,asc|desc]; campos: id, pickupDate, dueDate, "
                    + "returnDate", example = "pickupDate,desc")
            @RequestParam(defaultValue = ListLoansQuery.DEFAULT_SORT) String sort) {
        var query = new ListLoansQuery(readerId, copyId, status, PageQuery.of(page, size, sort));
        return PageResponse.from(listLoansUseCase.list(query), LoanDetailsResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar empréstimo por id",
            description = "Devolve o empréstimo com as datas de retirada, prevista e de devolução e, se já foi "
                    + "devolvido, os dias de atraso e a multa e a suspensão geradas, quando houver. Vale também "
                    + "para empréstimo encerrado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Empréstimo encontrado"),
            @ApiResponse(responseCode = "404", description = "Empréstimo não encontrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    LoanDetailsResponse findById(@PathVariable Long id) {
        return getLoanUseCase.findById(id)
                .map(LoanDetailsResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("empréstimo não encontrado: " + id));
    }
}
