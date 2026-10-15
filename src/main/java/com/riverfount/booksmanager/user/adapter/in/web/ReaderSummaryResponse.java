package com.riverfount.booksmanager.user.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.user.domain.Reader;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Reader item of the listing: separate from the detail (ReaderResponse) so
 * that the listing exposes the document masked and leaves out the birth
 * date, the guardian and the phone (RNF11).
 */
record ReaderSummaryResponse(
        @Schema(requiredMode = REQUIRED, description = "Identificador do leitor", example = "1")
        Long id,
        @Schema(requiredMode = REQUIRED, description = "Nome do leitor", example = "Maria Silva")
        String name,
        @Schema(requiredMode = REQUIRED, description = "Documento mascarado: só os dois últimos caracteres "
                + "aparecem; o documento completo está na consulta por id", example = "*********00")
        String maskedDocument,
        @Schema(requiredMode = REQUIRED, description = "E-mail do leitor", example = "maria@example.com")
        String email,
        @Schema(requiredMode = REQUIRED, description = "Se o leitor está ativo", example = "true")
        boolean active,
        @Schema(requiredMode = REQUIRED, description = "Se o leitor está bloqueado", example = "false")
        boolean blocked) {

    static ReaderSummaryResponse from(Reader reader) {
        return new ReaderSummaryResponse(reader.getId(), reader.getName(),
                DocumentMask.mask(reader.getDocument()), reader.getEmail(), reader.isActive(),
                reader.isBlocked());
    }
}
