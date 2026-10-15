package com.riverfount.booksmanager.user.adapter.in.web;

import com.riverfount.booksmanager.user.domain.Reader;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Partial update of a reader: omitted (or null) fields keep their current
 * value, so a field cannot be cleared by this request. Sent fields follow the
 * same format rules as registration. Only the personal data is editable; the
 * state (active, blocked) has its own routes and is ignored if sent.
 */
record UpdateReaderRequest(
        @Schema(description = "Novo nome do leitor (opcional)", example = "Maria Souza")
        @Size(max = Reader.MAX_NAME_LENGTH, message = "o nome deve ter até " + Reader.MAX_NAME_LENGTH
                + " caracteres")
        @Pattern(regexp = "^\\S(.*\\S)?$",
                message = "o nome não pode ficar em branco nem ter espaços nas pontas")
        String name,
        @Schema(description = "Novo documento (opcional); continua único por leitor", example = "123.456.789-00")
        @Pattern(regexp = ".*\\S.*", message = "o documento não pode ficar em branco")
        String document,
        @Schema(description = "Novo e-mail (opcional)", example = "maria@example.com")
        @Pattern(regexp = ".*\\S.*", message = "o e-mail não pode ficar em branco")
        String email,
        @Schema(description = "Novo telefone (opcional)", example = "11 91234-5678")
        String phone,
        @Schema(description = "Nova data de nascimento (ISO-8601, opcional)", example = "1990-05-10")
        LocalDate birthDate,
        @Schema(description = "Responsável (opcional): os campos enviados substituem os atuais, um a um; "
                + "o leitor menor de 18 anos precisa ficar com nome e e-mail do responsável")
        @Valid
        RegisterReaderRequest.GuardianRequest guardian) {
}
