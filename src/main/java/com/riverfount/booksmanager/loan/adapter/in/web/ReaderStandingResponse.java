package com.riverfount.booksmanager.loan.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.loan.application.port.in.ReaderStanding;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * How the reader stands today, as they see it (Phase 6): whether they can borrow and why not, what they owe, the
 * suspension in force and the counts against the limits. Nothing about copies or other readers, no reason for a block
 * and no id of a fine or a loan.
 */
record ReaderStandingResponse(
        @Schema(requiredMode = REQUIRED, description = "Se o leitor poderia pegar um livro emprestado hoje, no que "
                + "depende dele (o exemplar e o livro não entram na conta)", example = "false")
        boolean canBorrow,
        @Schema(requiredMode = REQUIRED, description = "Se um operador bloqueou o leitor. O motivo do bloqueio não é "
                + "mostrado", example = "false")
        boolean blocked,
        @Schema(requiredMode = REQUIRED, description = "Os motivos que hoje impedem o empréstimo, os mesmos da recusa "
                + "no balcão e na mesma ordem; vazia quando `canBorrow` é verdadeiro",
                example = "[\"o leitor tem multa pendente\"]")
        List<String> reasons,
        @Schema(requiredMode = REQUIRED, description = "Total das multas pendentes (R$); zero quando não há",
                example = "6.00")
        BigDecimal pendingFineTotal,
        @Schema(description = "A suspensão em vigor hoje; nula quando o leitor não está suspenso")
        SuspensionInForce suspension,
        @Schema(requiredMode = REQUIRED, description = "Quantos empréstimos ativos o leitor tem", example = "2")
        long activeLoans,
        @Schema(requiredMode = REQUIRED, description = "Quantos deles estão em atraso (vencer hoje ainda é estar em "
                + "dia)", example = "0")
        long overdueLoans,
        @Schema(requiredMode = REQUIRED, description = "Limite de empréstimos simultâneos da biblioteca",
                example = "3")
        int loanLimit,
        @Schema(requiredMode = REQUIRED, description = "Quantas reservas ativas o leitor tem, contadas à parte dos "
                + "empréstimos", example = "1")
        long activeReservations,
        @Schema(requiredMode = REQUIRED, description = "Limite de reservas ativas da biblioteca", example = "3")
        int reservationLimit) {

    /** The suspension covers the days from {@code startDate} until the day before {@code releaseDate}. */
    record SuspensionInForce(
            @Schema(requiredMode = REQUIRED, description = "Primeiro dia da suspensão (ISO-8601)",
                    example = "2026-11-10")
            LocalDate startDate,
            @Schema(requiredMode = REQUIRED, description = "Dia em que o leitor volta a poder emprestar (ISO-8601)",
                    example = "2026-11-14")
            LocalDate releaseDate) {
    }

    static ReaderStandingResponse from(ReaderStanding standing) {
        var suspension = standing.suspension() == null ? null
                : new SuspensionInForce(standing.suspension().startDate(), standing.suspension().releaseDate());
        return new ReaderStandingResponse(standing.canBorrow(), standing.blocked(), standing.reasons(),
                standing.pendingFineTotal(), suspension, standing.activeLoans(), standing.overdueLoans(),
                standing.loanLimit(), standing.activeReservations(), standing.reservationLimit());
    }
}
