package com.riverfount.booksmanager.user.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.user.domain.Role;
import com.riverfount.booksmanager.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * A system user as the API shows it: never the password, never its hash, and
 * not the count of wrong passwords. {@code blocked} says whether the login
 * lockout is in force at the moment of the request (RF08); a block that has
 * already ended is shown as not blocked, with no date.
 */
record UserResponse(
        @Schema(requiredMode = REQUIRED, description = "Identificador do usuário", example = "1")
        Long id,
        @Schema(requiredMode = REQUIRED, description = "Login do usuário, em minúsculas", example = "maria")
        String login,
        @Schema(description = "Nome de exibição; nulo quando o usuário não tem", example = "Maria Silva")
        String displayName,
        @Schema(requiredMode = REQUIRED, description = "Perfil do usuário", example = "OPERATOR")
        Role role,
        @Schema(requiredMode = REQUIRED, description = "Se o usuário está ativo", example = "true")
        boolean active,
        @Schema(requiredMode = REQUIRED, example = "false",
                description = "Se o usuário está bloqueado agora por ter errado a senha 3 vezes seguidas; o "
                        + "bloqueio termina sozinho")
        boolean blocked,
        @Schema(description = "Até quando dura o bloqueio (ISO-8601); só aparece enquanto ele está em vigor",
                example = "2026-10-15T12:15:00Z")
        Instant blockedUntil) {

    static UserResponse from(User user, Instant now) {
        var blocked = user.isBlocked(now);
        return new UserResponse(user.getId(), user.getLogin(), user.getDisplayName(), user.getRole(),
                user.isActive(), blocked, blocked ? user.getBlockedUntil() : null);
    }
}
