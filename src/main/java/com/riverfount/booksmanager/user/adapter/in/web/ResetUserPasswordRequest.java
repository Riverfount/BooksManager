package com.riverfount.booksmanager.user.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The new password of a system user. The size only keeps absurd payloads away from
 * the password hashing: the real rules (the password policy) are the domain's and
 * answer 422 with a message that names them.
 */
record ResetUserPasswordRequest(
        @Schema(description = "Nova senha: de 8 a 25 caracteres, com ao menos uma letra maiúscula, uma minúscula, "
                + "um número e um caractere especial. É guardada só como hash e nunca é devolvida.",
                example = "N3w-Pass#word")
        @NotBlank(message = "a senha é obrigatória")
        @Size(max = 128, message = "a senha deve ter até 25 caracteres")
        String password) {

    /** Without the password, so the string form is safe to log. */
    @Override
    public String toString() {
        return "ResetUserPasswordRequest[password=***]";
    }
}
