package com.riverfount.booksmanager.user.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.user.domain.Role;
import com.riverfount.booksmanager.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

/** A system user as the API shows it: never the password, never its hash. */
record UserResponse(
        @Schema(requiredMode = REQUIRED, description = "Identificador do usuário", example = "1")
        Long id,
        @Schema(requiredMode = REQUIRED, description = "Login do usuário, em minúsculas", example = "maria")
        String login,
        @Schema(requiredMode = REQUIRED, description = "Perfil do usuário", example = "OPERATOR")
        Role role,
        @Schema(requiredMode = REQUIRED, description = "Se o usuário está ativo", example = "true")
        boolean active) {

    static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getLogin(), user.getRole(), user.isActive());
    }
}
