package com.riverfount.booksmanager.loan.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** The reservation a reader makes for themselves (Phase 6): only the book. The reader is the one of the token. */
record ReserveMyBookRequest(
        @Schema(description = "Id do livro a reservar", example = "30")
        @NotNull(message = "o livro é obrigatório")
        Long bookId) {
}
