package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.domain.Copy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record RegisterCopyRequest(
        @Schema(description = "Id do livro, já cadastrado e ativo", example = "1")
        @NotNull(message = "o livro é obrigatório")
        Long bookId,
        @Schema(description = "Código de patrimônio, único por exemplar", example = "PAT-0001")
        @NotBlank(message = "o código de patrimônio é obrigatório")
        @Size(max = Copy.MAX_ASSET_CODE_LENGTH,
                message = "o código de patrimônio deve ter até " + Copy.MAX_ASSET_CODE_LENGTH
                        + " caracteres")
        // an empty or blank text is for @NotBlank to report (#368); this only flags spaces around a text with content
        @Pattern(regexp = "^(\\s*|\\S(.*\\S)?)$", message = "o código de patrimônio não pode ter espaços nas pontas")
        String assetCode) {
}
