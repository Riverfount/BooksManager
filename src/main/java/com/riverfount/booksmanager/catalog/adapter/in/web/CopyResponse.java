package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.domain.Copy;
import io.swagger.v3.oas.annotations.media.Schema;

record CopyResponse(
        @Schema(description = "Identificador do exemplar", example = "1")
        Long id,
        @Schema(description = "Id do livro", example = "1")
        Long bookId,
        @Schema(description = "Código de patrimônio", example = "PAT-0001")
        String assetCode,
        @Schema(description = "Status do exemplar", example = "AVAILABLE",
                allowableValues = {"AVAILABLE", "BORROWED", "RESERVED", "DAMAGED", "LOST"})
        String status,
        @Schema(description = "Se o exemplar está ativo", example = "true")
        boolean active) {

    static CopyResponse from(Copy copy) {
        return new CopyResponse(copy.getId(), copy.getBookId(), copy.getAssetCode(),
                copy.getStatus().name(), copy.isActive());
    }
}
