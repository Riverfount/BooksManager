package com.riverfount.booksmanager.user.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The new password of a reader, with the token of the reset link. The password policy is the domain's (422). */
record ResetReaderPasswordRequest(
        @Schema(description = "Token do link de redefinição, como veio no e-mail", example = "dGhpcy1pcy1ub3QtYS1yZWFsLXRva2Vu")
        @NotBlank(message = "o token do link é obrigatório")
        @Size(max = 200, message = "o token do link é inválido")
        String token,
        @Schema(description = "Nova senha: de 8 a 25 caracteres, com ao menos uma letra maiúscula, uma minúscula, um "
                + "número e um caractere especial. É guardada só como hash e nunca é devolvida.",
                example = "N3w-Pass#word")
        @NotBlank(message = "a senha é obrigatória")
        @Size(max = 128, message = "a senha deve ter até 25 caracteres")
        String password) {

    /** Without the token and the password, so the string form is safe to log. */
    @Override
    public String toString() {
        return "ResetReaderPasswordRequest[token=***, password=***]";
    }
}
