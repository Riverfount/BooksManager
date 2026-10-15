package com.riverfount.booksmanager.user.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.user.domain.Guardian;
import com.riverfount.booksmanager.user.domain.Reader;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

record ReaderResponse(
        @Schema(requiredMode = REQUIRED, description = "Identificador do leitor", example = "1")
        Long id,
        @Schema(requiredMode = REQUIRED, description = "Nome do leitor", example = "Maria Silva")
        String name,
        @Schema(requiredMode = REQUIRED, description = "Documento completo, normalizado", example = "12345678900")
        String document,
        @Schema(requiredMode = REQUIRED, description = "E-mail do leitor", example = "maria@example.com")
        String email,
        @Schema(description = "Telefone do leitor", example = "11 91234-5678")
        String phone,
        @Schema(requiredMode = REQUIRED, description = "Data de nascimento (ISO-8601)", example = "1990-05-10")
        LocalDate birthDate,
        @Schema(description = "Responsável; só existe quando foi informado")
        GuardianResponse guardian,
        @Schema(requiredMode = REQUIRED, description = "Se o leitor está ativo", example = "true")
        boolean active,
        @Schema(requiredMode = REQUIRED, description = "Se o leitor está bloqueado", example = "false")
        boolean blocked) {

    static ReaderResponse from(Reader reader) {
        return new ReaderResponse(reader.getId(), reader.getName(), reader.getDocument(), reader.getEmail(),
                reader.getPhone(), reader.getBirthDate(), GuardianResponse.from(reader.getGuardian()),
                reader.isActive(), reader.isBlocked());
    }

    record GuardianResponse(
            @Schema(requiredMode = REQUIRED, description = "Nome do responsável", example = "Ana Souza")
            String name,
            @Schema(requiredMode = REQUIRED, description = "E-mail do responsável", example = "ana@example.com")
            String email,
            @Schema(description = "Telefone do responsável", example = "11 99999-0000")
            String phone) {

        static GuardianResponse from(Guardian guardian) {
            return guardian == null ? null
                    : new GuardianResponse(guardian.name(), guardian.email(), guardian.phone());
        }
    }
}
