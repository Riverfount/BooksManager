package com.riverfount.booksmanager.user.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** "I forgot my password": the e-mail the reader is registered with. */
record ForgotPasswordRequest(
        @Schema(description = "E-mail com que o leitor está cadastrado", example = "ana@example.com")
        @NotBlank(message = "o e-mail é obrigatório")
        @Size(max = 254, message = "o e-mail deve ter até 254 caracteres")
        String email) {
}
