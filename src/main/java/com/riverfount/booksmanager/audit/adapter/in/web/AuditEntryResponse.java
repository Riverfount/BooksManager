package com.riverfount.booksmanager.audit.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditEntry;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

record AuditEntryResponse(
        @Schema(requiredMode = REQUIRED, description = "Identificador do registro", example = "128")
        Long id,
        @Schema(requiredMode = REQUIRED, description = "Quando aconteceu (ISO-8601, UTC)",
                example = "2026-10-24T15:00:00Z")
        Instant occurredAt,
        @Schema(description = "Id do usuário do sistema que fez a operação; nulo quando ninguém a fez (o "
                + "administrador inicial criado na subida, um login que não nomeia um usuário conhecido)",
                example = "7")
        Long actorId,
        @Schema(description = "Login desse usuário, guardado junto do id; nulo quando não há autor", example = "maria")
        String actorLogin,
        @Schema(requiredMode = REQUIRED, description = "A operação registrada", example = "FINE_WAIVED")
        AuditAction action,
        @Schema(description = "O tipo do alvo da operação; nulo quando ela não tem alvo (um login falho de um "
                + "usuário desconhecido)", example = "FINE")
        AuditTarget targetType,
        @Schema(description = "O id do alvo; nulo quando o alvo não tem id (a configuração da biblioteca) ou não há "
                + "alvo", example = "42")
        Long targetId,
        @Schema(description = "Resumo curto da operação, como o motivo digitado num abono (no máximo 500 "
                + "caracteres); nunca traz senha, hash, token nem dado pessoal além do necessário",
                example = "multa abonada: erro de cálculo")
        String summary) {

    static AuditEntryResponse from(AuditEntry entry) {
        return new AuditEntryResponse(entry.id(), entry.occurredAt(), entry.actorId(), entry.actorLogin(),
                entry.action(), entry.target(), entry.targetId(), entry.summary());
    }
}
