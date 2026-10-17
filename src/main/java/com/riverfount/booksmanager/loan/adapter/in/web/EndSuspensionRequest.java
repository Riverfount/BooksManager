package com.riverfount.booksmanager.loan.adapter.in.web;

import com.riverfount.booksmanager.loan.domain.Suspension;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record EndSuspensionRequest(
        @Schema(description = "Justificativa do encerramento antecipado, obrigatória; fica registrada na suspensão",
                example = "Devolução regularizada com a direção")
        @NotBlank(message = "a justificativa do encerramento antecipado é obrigatória")
        @Size(max = Suspension.MAX_REASON_LENGTH,
                message = "a justificativa do encerramento antecipado deve ter até " + Suspension.MAX_REASON_LENGTH
                        + " caracteres")
        String reason) {
}
