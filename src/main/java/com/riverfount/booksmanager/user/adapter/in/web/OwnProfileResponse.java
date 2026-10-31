package com.riverfount.booksmanager.user.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.user.application.port.in.OwnProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * The registration of the reader of the token (RF32): only their own data, with the document masked (RNF11), and
 * without the id or the state (blocked, active), which are not the reader's to read here.
 */
record OwnProfileResponse(
        @Schema(requiredMode = REQUIRED, description = "Nome do leitor; só a equipe altera", example = "Maria Silva")
        String name,
        @Schema(requiredMode = REQUIRED, description = "Documento mascarado (só os dois últimos caracteres aparecem); "
                + "só a equipe altera", example = "*********00")
        String document,
        @Schema(requiredMode = REQUIRED, description = "E-mail do leitor", example = "maria@example.com")
        String email,
        @Schema(description = "Telefone do leitor", example = "11 91234-5678")
        String phone,
        @Schema(requiredMode = REQUIRED, description = "Data de nascimento (ISO-8601); só a equipe altera",
                example = "1990-05-10")
        LocalDate birthDate,
        @Schema(requiredMode = REQUIRED, description = "Se o leitor tem menos de 18 anos hoje; só então o responsável "
                + "pode ser alterado", example = "false")
        boolean minor,
        @Schema(description = "Responsável; só existe quando foi informado")
        ReaderResponse.GuardianResponse guardian) {

    static OwnProfileResponse from(OwnProfile profile) {
        var reader = profile.reader();
        return new OwnProfileResponse(reader.getName(), DocumentMask.mask(reader.getDocument()), reader.getEmail(),
                reader.getPhone(), reader.getBirthDate(), profile.minor(),
                ReaderResponse.GuardianResponse.from(reader.getGuardian()));
    }
}
