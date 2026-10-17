package com.riverfount.booksmanager.loan.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

record RegisterLoanRequest(
        @Schema(description = "Id do exemplar a emprestar", example = "7")
        @NotNull(message = "o exemplar é obrigatório")
        Long copyId,
        @Schema(description = "Id do leitor que leva o exemplar", example = "20")
        @NotNull(message = "o leitor é obrigatório")
        Long readerId) {
}
