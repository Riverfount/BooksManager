package com.riverfount.booksmanager.user.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * What the reader may change about themself (RF32): phone, e-mail and guardian. Omitted (or null) fields keep their
 * current value. Name, document, birth date and state are not part of the contract and are ignored if sent.
 */
record UpdateOwnProfileRequest(
        @Schema(description = "Novo telefone (opcional)", example = "11 91234-5678")
        String phone,
        @Schema(description = "Novo e-mail (opcional). Trocar o e-mail exige a senha atual (`currentPassword`), porque "
                + "é para ele que vão o convite e a redefinição de senha.", example = "maria@example.com")
        @Pattern(regexp = ".*\\S.*", message = "o e-mail não pode ficar em branco")
        String email,
        @Schema(description = "Responsável (opcional), só para o leitor menor de 18 anos: os campos enviados "
                + "substituem os atuais, um a um")
        @Valid
        RegisterReaderRequest.GuardianRequest guardian,
        @Schema(description = "Senha atual; obrigatória só quando o e-mail muda. Nunca é devolvida.",
                example = "Cur#rent1Pass")
        @Size(max = 128, message = "a senha atual deve ter até 128 caracteres")
        String currentPassword) {

    /** Without the password, so the string form is safe to log. */
    @Override
    public String toString() {
        return "UpdateOwnProfileRequest[currentPassword=***, ...]";
    }
}
