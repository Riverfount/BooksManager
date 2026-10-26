package com.riverfount.booksmanager.user.adapter.in.web;

import com.riverfount.booksmanager.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The reader accepts the invitation: the token of the link, and the login and password they choose. The sizes only
 * keep absurd payloads away; the real rules (login without spaces, password policy) are the domain's and answer 422.
 */
record AcceptInvitationRequest(
        @Schema(description = "Token do link do convite, como veio no e-mail", example = "dGhpcy1pcy1ub3QtYS1yZWFsLXRva2Vu")
        @NotBlank(message = "o token do convite é obrigatório")
        @Size(max = 200, message = "o token do convite é inválido")
        String token,
        @Schema(description = "Login que o leitor escolhe: sem espaços e com até 50 caracteres; não diferencia "
                + "maiúsculas e deve ser único", example = "ana.souza")
        @NotBlank(message = "o login é obrigatório")
        @Size(max = User.MAX_LOGIN_LENGTH, message = "o login deve ter até " + User.MAX_LOGIN_LENGTH + " caracteres")
        String login,
        @Schema(description = "Senha: de 8 a 25 caracteres, com ao menos uma letra maiúscula, uma minúscula, um número "
                + "e um caractere especial. É guardada só como hash e nunca é devolvida.", example = "N3w-Pass#word")
        @NotBlank(message = "a senha é obrigatória")
        @Size(max = 128, message = "a senha deve ter até 25 caracteres")
        String password) {

    /** Without the token and the password, so the string form is safe to log. */
    @Override
    public String toString() {
        return "AcceptInvitationRequest[token=***, login=" + login + ", password=***]";
    }
}
