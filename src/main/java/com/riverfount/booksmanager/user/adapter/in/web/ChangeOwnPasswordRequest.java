package com.riverfount.booksmanager.user.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The password change of the user of the token: the reader (RF32) or the staff (RF09). The password policy is the domain's (422). */
record ChangeOwnPasswordRequest(
        @Schema(description = "Senha atual. Se estiver errada, a troca é recusada e a tentativa conta para o "
                + "bloqueio da conta.", example = "Cur#rent1Pass")
        @NotBlank(message = "a senha atual é obrigatória")
        @Size(max = 128, message = "a senha atual deve ter até 128 caracteres")
        String currentPassword,
        @Schema(description = "Nova senha: de 8 a 25 caracteres, com ao menos uma letra maiúscula, uma minúscula, um "
                + "número e um caractere especial. É guardada só como hash e nunca é devolvida.",
                example = "N3w-Pass#word")
        @NotBlank(message = "a nova senha é obrigatória")
        @Size(max = 128, message = "a nova senha deve ter até 25 caracteres")
        String newPassword) {

    /** Without the passwords, so the string form is safe to log. */
    @Override
    public String toString() {
        return "ChangeOwnPasswordRequest[currentPassword=***, newPassword=***]";
    }
}
