package com.riverfount.booksmanager.user.adapter.in.web;

import com.riverfount.booksmanager.user.domain.Reader;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

record RegisterReaderRequest(
        @Schema(description = "Nome do leitor", example = "Maria Silva")
        @NotBlank(message = "o nome é obrigatório")
        @Size(max = Reader.MAX_NAME_LENGTH, message = "o nome deve ter até " + Reader.MAX_NAME_LENGTH
                + " caracteres")
        @Pattern(regexp = "^\\S(.*\\S)?$", message = "o nome não pode ter espaços nas pontas")
        String name,
        @Schema(description = "Documento (CPF, matrícula etc.); pontuação e espaços são ignorados e as "
                + "letras ficam em maiúsculas. Único por leitor.", example = "123.456.789-00")
        @NotBlank(message = "o documento é obrigatório")
        String document,
        @Schema(description = "E-mail do leitor", example = "maria@example.com")
        @NotBlank(message = "o e-mail é obrigatório")
        String email,
        @Schema(description = "Telefone do leitor (opcional)", example = "11 91234-5678")
        String phone,
        @Schema(description = "Data de nascimento (ISO-8601), para saber se o leitor tem menos de 18 anos",
                example = "1990-05-10")
        @NotNull(message = "a data de nascimento é obrigatória")
        LocalDate birthDate,
        @Schema(description = "Responsável, obrigatório (nome e e-mail) para leitor menor de 18 anos")
        @Valid
        GuardianRequest guardian) {

    /**
     * The guardian's fields are not marked as required here: the domain
     * decides (a reader under 18 without the name or e-mail is refused with
     * 422 and a clear message).
     */
    record GuardianRequest(
            @Schema(description = "Nome do responsável", example = "Ana Souza")
            String name,
            @Schema(description = "E-mail do responsável", example = "ana@example.com")
            String email,
            @Schema(description = "Telefone do responsável (opcional)", example = "11 99999-0000")
            String phone) {
    }
}
