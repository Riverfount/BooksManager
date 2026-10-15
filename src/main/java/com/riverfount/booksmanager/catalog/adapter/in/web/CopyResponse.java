package com.riverfount.booksmanager.catalog.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.catalog.domain.Copy;
import io.swagger.v3.oas.annotations.media.Schema;

record CopyResponse(
        @Schema(requiredMode = REQUIRED, description = "Identificador do exemplar", example = "1")
        Long id,
        @Schema(requiredMode = REQUIRED, description = "Id do livro", example = "1")
        Long bookId,
        @Schema(requiredMode = REQUIRED, description = "Código de patrimônio", example = "PAT-0001")
        String assetCode,
        @Schema(requiredMode = REQUIRED, description = "Status do exemplar", example = "AVAILABLE",
                allowableValues = {"AVAILABLE", "BORROWED", "RESERVED", "DAMAGED", "LOST"})
        String status,
        @Schema(requiredMode = REQUIRED, description = "Se o exemplar está ativo", example = "true")
        boolean active) {

    static CopyResponse from(Copy copy) {
        return new CopyResponse(copy.getId(), copy.getBookId(), copy.getAssetCode(),
                copy.getStatus().name(), copy.isActive());
    }
}
