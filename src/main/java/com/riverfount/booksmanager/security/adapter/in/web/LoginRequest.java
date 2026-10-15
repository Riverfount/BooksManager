package com.riverfount.booksmanager.security.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credentials to log in. The sizes only keep absurd payloads away from the
 * password hashing: they are above what the user module accepts for a login
 * (50) and a password (25), and they say nothing about the real limits.
 */
record LoginRequest(
        @Schema(description = "Login do usuário (não diferencia maiúsculas)", example = "maria")
        @NotBlank(message = "o login é obrigatório")
        @Size(max = 50, message = "o login deve ter até 50 caracteres")
        String login,
        @Schema(description = "Senha do usuário", example = "S3cret-Pass!")
        @NotBlank(message = "a senha é obrigatória")
        @Size(max = 128, message = "a senha deve ter até 128 caracteres")
        String password) {

    /** Without the password, so the string form is safe to log. */
    @Override
    public String toString() {
        return "LoginRequest[login=" + login + ", password=***]";
    }
}
