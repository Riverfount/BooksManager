package com.riverfount.booksmanager.loan.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

record ReserveBookRequest(
        @Schema(description = "Id do livro a reservar", example = "30")
        @NotNull(message = "o livro é obrigatório")
        Long bookId,
        @Schema(description = "Id do leitor que reserva", example = "20")
        @NotNull(message = "o leitor é obrigatório")
        Long readerId) {
}
