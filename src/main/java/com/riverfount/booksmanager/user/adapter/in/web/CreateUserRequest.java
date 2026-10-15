package com.riverfount.booksmanager.user.adapter.in.web;

import com.riverfount.booksmanager.user.domain.Role;
import com.riverfount.booksmanager.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Data to create a system user. The sizes only keep absurd payloads away from
 * the password hashing: the real rules (login without spaces, password
 * policy) are the domain's and answer 422 with a message that names them.
 */
record CreateUserRequest(
        @Schema(description = "Login do usuário: sem espaços e com até 50 caracteres; não diferencia maiúsculas "
                + "(é guardado em minúsculas) e deve ser único", example = "maria")
        @NotBlank(message = "o login é obrigatório")
        @Size(max = User.MAX_LOGIN_LENGTH, message = "o login deve ter até " + User.MAX_LOGIN_LENGTH + " caracteres")
        String login,
        @Schema(description = "Senha inicial: de 8 a 25 caracteres, com ao menos uma letra maiúscula, uma minúscula, "
                + "um número e um caractere especial. É guardada só como hash e nunca é devolvida.",
                example = "N3w-Pass#word")
        @NotBlank(message = "a senha é obrigatória")
        @Size(max = 128, message = "a senha deve ter até 25 caracteres")
        String password,
        @Schema(description = "Perfil do usuário: ADMIN gerencia usuários e configuração; OPERATOR opera o dia a dia",
                example = "OPERATOR")
        @NotNull(message = "o perfil é obrigatório")
        Role role) {

    /** Without the password, so the string form is safe to log. */
    @Override
    public String toString() {
        return "CreateUserRequest[login=" + login + ", password=***, role=" + role + "]";
    }
}
