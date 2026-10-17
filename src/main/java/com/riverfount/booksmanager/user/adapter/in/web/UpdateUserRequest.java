package com.riverfount.booksmanager.user.adapter.in.web;

import com.riverfount.booksmanager.user.domain.Role;
import com.riverfount.booksmanager.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Partial update of a system user: only the profile and the display name can be changed here, and a
 * omitted or null field keeps the current value. The login never changes, the
 * password has its own route and the activation is the deactivation's, so
 * those fields, if sent, are ignored.
 */
record UpdateUserRequest(
        @Schema(description = "Novo perfil do usuário (opcional): ADMIN ou OPERATOR. Não é possível tirar o perfil "
                + "de administrador do último administrador ativo.", example = "ADMIN")
        Role role,
        @Schema(description = "Novo nome de exibição (opcional). Omitido ou nulo mantém o atual; um texto em "
                + "branco o remove. Até " + User.MAX_DISPLAY_NAME_LENGTH + " caracteres.", example = "Maria Silva")
        @Size(max = User.MAX_DISPLAY_NAME_LENGTH, message = "o nome de exibição deve ter até "
                + User.MAX_DISPLAY_NAME_LENGTH + " caracteres")
        String displayName) {
}
