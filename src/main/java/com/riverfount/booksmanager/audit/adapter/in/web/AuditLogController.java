package com.riverfount.booksmanager.audit.adapter.in.web;

import com.riverfount.booksmanager.audit.application.port.in.ListAuditLogQuery;
import com.riverfount.booksmanager.audit.application.port.in.ListAuditLogUseCase;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.shared.adapter.in.web.AdminOnly;
import com.riverfount.booksmanager.shared.adapter.in.web.PageResponse;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The audit trail (RF27). Only the administrator reads it, and there is no route to add, change or remove a
 * line: the trail is written by the use cases themselves, never through the API.
 */
@AdminOnly
@RestController
@RequestMapping("/api/v1/audit-log")
@Tag(name = "Audit", description = "Trilha de auditoria das operações sensíveis, só para administradores (RF27).")
class AuditLogController {

    private final ListAuditLogUseCase listAuditLogUseCase;

    AuditLogController(ListAuditLogUseCase listAuditLogUseCase) {
        this.listAuditLogUseCase = listAuditLogUseCase;
    }

    @GetMapping
    @Operation(summary = "Consultar a trilha de auditoria",
            description = "Lista os registros da trilha de auditoria (RF27), do mais recente para o mais antigo, de "
                    + "forma paginada (máximo de 100 por página), só para administrador: o operador recebe 403. Cada "
                    + "registro diz quem fez (id e login do usuário do sistema; nulos quando ninguém fez), quando, o "
                    + "quê (`action`), em quê (`targetType` e `targetId`) e um resumo curto. Todos os filtros são "
                    + "opcionais e se combinam: `actorId`, `action`, `targetType`, `targetId` (que exige o "
                    + "`targetType`) e o período `from` e `to`, com as duas datas incluídas, no fuso "
                    + "`America/Sao_Paulo`. A ordem é fixa; qualquer `sort` enviado é ignorado. Os registros só são "
                    + "acrescentados pelas próprias operações: não há rota para criar, alterar nem apagar um.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de registros"),
            @ApiResponse(responseCode = "400",
                    description = "Filtro inválido (ação ou tipo de alvo desconhecidos, `targetId` sem "
                            + "`targetType`, data malformada ou período invertido) ou paginação inválida (inclusive "
                            + "tamanho acima de 100)",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    PageResponse<AuditEntryResponse> list(
            @Parameter(description = "Id do usuário do sistema que fez a operação", example = "7")
            @RequestParam(required = false) Long actorId,
            @Parameter(description = "A operação registrada") @RequestParam(required = false) AuditAction action,
            @Parameter(description = "O tipo do alvo da operação") @RequestParam(required = false)
            AuditTarget targetType,
            @Parameter(description = "O id do alvo; exige `targetType`", example = "42")
            @RequestParam(required = false) Long targetId,
            @Parameter(description = "Primeiro dia do período, incluído (AAAA-MM-DD)", example = "2026-10-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Último dia do período, incluído (AAAA-MM-DD)", example = "2026-10-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Número da página, a partir de 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página, de 1 a 100")
            @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size) {
        return PageResponse.from(
                listAuditLogUseCase.list(ListAuditLogQuery.of(actorId, action, targetType, targetId, from, to, page, size)),
                AuditEntryResponse::from);
    }
}
