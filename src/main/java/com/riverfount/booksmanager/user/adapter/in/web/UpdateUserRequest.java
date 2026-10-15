package com.riverfount.booksmanager.user.adapter.in.web;

import com.riverfount.booksmanager.user.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Partial update of a system user: only the profile can be changed here, and a
 * omitted or null field keeps the current value. The login never changes, the
 * password has its own route and the activation is the deactivation's, so
 * those fields, if sent, are ignored.
 */
record UpdateUserRequest(
        @Schema(description = "Novo perfil do usuário (opcional): ADMIN ou OPERATOR. Não é possível tirar o perfil "
                + "de administrador do último administrador ativo.", example = "ADMIN")
        Role role) {
}
