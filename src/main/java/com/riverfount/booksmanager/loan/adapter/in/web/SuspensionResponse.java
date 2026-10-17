package com.riverfount.booksmanager.loan.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.loan.application.port.in.SuspensionDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

record SuspensionResponse(
        @Schema(requiredMode = REQUIRED, description = "Identificador da suspensão", example = "6")
        Long id,
        @Schema(requiredMode = REQUIRED, description = "Id do leitor suspenso", example = "20")
        Long readerId,
        @Schema(requiredMode = REQUIRED, description = "Id do empréstimo atrasado que causou a suspensão",
                example = "42")
        Long loanId,
        @Schema(requiredMode = REQUIRED, description = "Primeiro dia suspenso (o da devolução)",
                example = "2026-11-18")
        LocalDate startDate,
        @Schema(requiredMode = REQUIRED, description = "Data de término calculada; fica como histórico mesmo se a "
                + "suspensão for encerrada antes", example = "2026-11-25")
        LocalDate endDate,
        @Schema(description = "Dia em que a suspensão foi encerrada antes do prazo; nulo se não foi",
                example = "2026-11-20")
        LocalDate endedEarlyOn,
        @Schema(description = "Justificativa do encerramento antecipado; nula se não houve", example = "Perdão")
        String endedEarlyReason,
        @Schema(requiredMode = REQUIRED, description = "Dia em que o leitor volta a poder emprestar: o término "
                + "calculado, ou o dia do encerramento antecipado quando veio antes", example = "2026-11-25")
        LocalDate releaseDate,
        @Schema(requiredMode = REQUIRED, description = "Se a suspensão está em vigor hoje; uma que já terminou, "
                + "sozinha ou antes, não está", example = "true")
        boolean inForce) {

    static SuspensionResponse from(SuspensionDetails details) {
        var suspension = details.suspension();
        return new SuspensionResponse(suspension.getId(), suspension.getReaderId(), suspension.getLoanId(),
                suspension.getStartDate(), suspension.getEndDate(), suspension.getEndedEarlyOn(),
                suspension.getEndedEarlyReason(), suspension.releaseDate(), details.inForce());
    }
}
