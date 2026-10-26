package com.riverfount.booksmanager.loan.adapter.in.web;

import com.riverfount.booksmanager.loan.application.port.in.GetOwnLoanQuery;
import com.riverfount.booksmanager.loan.application.port.in.GetOwnLoanUseCase;
import com.riverfount.booksmanager.loan.application.port.in.ListLoansQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListOwnLoansQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListOwnLoansUseCase;
import com.riverfount.booksmanager.loan.application.port.in.LoanStatus;
import com.riverfount.booksmanager.loan.application.port.in.RenewOwnLoanCommand;
import com.riverfount.booksmanager.loan.application.port.in.RenewOwnLoanUseCase;
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
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The loans of the reader (Phase 6, RF13, RF14): see their own loans, with the fine and the suspension a return
 * created, and renew one. The reader is always the one of the token ({@link CurrentReader}), never one sent in the
 * request, and the rules are the ones of the desk: only the answers change, to show the reader what is theirs and
 * nothing else (no copy, no other reader). The renewal takes no date: it is today, from the clock.
 */
@ReaderOnly
@RestController
@RequestMapping("/api/v1/me/loans")
@Tag(name = "Reader loans", description = "Os empréstimos do próprio leitor: consultar e renovar (RF13, RF14).")
class ReaderLoanController {

    private final ListOwnLoansUseCase listUseCase;
    private final GetOwnLoanUseCase getUseCase;
    private final RenewOwnLoanUseCase renewUseCase;

    ReaderLoanController(ListOwnLoansUseCase listUseCase, GetOwnLoanUseCase getUseCase,
            RenewOwnLoanUseCase renewUseCase) {
        this.listUseCase = listUseCase;
        this.getUseCase = getUseCase;
        this.renewUseCase = renewUseCase;
    }

    @GetMapping
    @Operation(summary = "Listar os meus empréstimos",
            description = "Lista os empréstimos do leitor logado, paginados (máximo de 100 por página), do mais "
                    + "recente para o mais antigo por padrão, por situação: `ACTIVE` (ainda não devolvidos) ou "
                    + "`RETURNED` (o histórico); sem o filtro, vêm todos. Com `overdue=true` vêm só os atrasados. "
                    + "Cada item traz o livro, as datas de retirada, prevista e de devolução, as renovações, os dias "
                    + "de atraso e, para o empréstimo devolvido, a multa e a suspensão geradas, quando houver. Nunca "
                    + "traz empréstimo de outro leitor nem dado do exemplar. Só para o perfil de leitor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de empréstimos do leitor"),
            @ApiResponse(responseCode = "400",
                    description = "Paginação, ordenação ou filtro inválidos (inclusive tamanho acima de 100 e "
                            + "situação desconhecida)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    PageResponse<ReaderLoanResponse> list(@CurrentReader Long readerId,
            @Parameter(description = "ACTIVE (ainda não devolvidos) ou RETURNED (encerrados); sem o parâmetro, ambos")
            @RequestParam(required = false) LoanStatus status,
            @Parameter(description = "Só os empréstimos atrasados: ativos com a data prevista anterior a hoje "
                    + "(vencer hoje ainda é estar em dia). Não vale com `status=RETURNED` (400)", example = "true")
            @RequestParam(defaultValue = "false") boolean overdue,
            @Parameter(description = "Número da página, a partir de 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página, de 1 a 100")
            @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size,
            @Parameter(description = "Ordenação no formato campo[,asc|desc]; campos: id, pickupDate, dueDate, "
                    + "returnDate", example = "pickupDate,desc")
            @RequestParam(defaultValue = ListLoansQuery.DEFAULT_SORT) String sort) {
        var query = new ListOwnLoansQuery(readerId, status, overdue, PageQuery.of(page, size, sort));
        return PageResponse.from(listUseCase.list(query), ReaderLoanResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar um empréstimo meu",
            description = "Devolve o empréstimo do leitor logado, também o já devolvido. Empréstimo de outro leitor "
                    + "devolve a mesma resposta 404 de um empréstimo que não existe. Só para o perfil de leitor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Empréstimo encontrado"),
            @ApiResponse(responseCode = "404", description = "Empréstimo não encontrado (ou de outro leitor)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ReaderLoanResponse findById(@CurrentReader Long readerId, @PathVariable Long id) {
        return ReaderLoanResponse.from(getUseCase.get(new GetOwnLoanQuery(id, readerId)));
    }

    @PostMapping("/{id}/renew")
    @Operation(summary = "Renovar um empréstimo meu",
            description = "Renova o empréstimo do leitor logado (RF13): a data prevista de devolução avança o prazo da "
                    + "configuração, contado a partir da data prevista atual, e a renovação é contada. Sem corpo: "
                    + "nenhuma data é aceita. Valem as regras do balcão (RN07, RN09): não renova empréstimo "
                    + "encerrado, em atraso, de livro inativo, com o limite de renovações atingido ou com outro "
                    + "leitor esperando o livro na fila de reservas. Empréstimo de outro leitor devolve 404, igual a "
                    + "um que não existe, antes de qualquer outra verificação. Só para o perfil de leitor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Empréstimo renovado, com a nova data prevista"),
            @ApiResponse(responseCode = "404", description = "Empréstimo não encontrado (ou de outro leitor)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409",
                    description = "Outra requisição alterou o mesmo empréstimo ao mesmo tempo; tente novamente",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422",
                    description = "Renovação não permitida (a mensagem diz o motivo): empréstimo encerrado (RN09), "
                            + "em atraso, livro inativo (RN14), limite de renovações atingido ou outro leitor "
                            + "esperando o livro na fila (RN07)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ReaderLoanResponse renew(@CurrentReader Long readerId, @PathVariable Long id) {
        return ReaderLoanResponse.from(renewUseCase.renew(new RenewOwnLoanCommand(id, readerId)));
    }
}
