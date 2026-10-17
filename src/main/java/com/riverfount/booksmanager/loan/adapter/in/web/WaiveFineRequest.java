package com.riverfount.booksmanager.loan.adapter.in.web;

import com.riverfount.booksmanager.loan.domain.Fine;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record WaiveFineRequest(
        @Schema(description = "Justificativa do abono, obrigatória; fica registrada na multa", example = "Internação do leitor")
        @NotBlank(message = "a justificativa do abono é obrigatória")
        @Size(max = Fine.MAX_WAIVER_REASON_LENGTH,
                message = "a justificativa do abono deve ter até " + Fine.MAX_WAIVER_REASON_LENGTH + " caracteres")
        String reason) {
}
