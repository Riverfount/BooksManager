package com.riverfount.booksmanager.user.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.user.domain.Role;
import com.riverfount.booksmanager.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Who is logged in, as the front-end shows them: the id, the login, the display name and the profile.
 * Never the password, its hash or the state of the login lockout.
 */
record CurrentUserResponse(
        @Schema(requiredMode = REQUIRED, description = "Identificador do usuário", example = "1")
        Long id,
        @Schema(requiredMode = REQUIRED, description = "Login do usuário, em minúsculas", example = "carlos")
        String login,
        @Schema(description = "Nome de exibição; nulo quando o usuário não tem (use o login)",
                example = "Carlos Mendes")
        String displayName,
        @Schema(requiredMode = REQUIRED, description = "Perfil do usuário", example = "OPERATOR")
        Role role) {

    static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(user.getId(), user.getLogin(), user.getDisplayName(), user.getRole());
    }
}
