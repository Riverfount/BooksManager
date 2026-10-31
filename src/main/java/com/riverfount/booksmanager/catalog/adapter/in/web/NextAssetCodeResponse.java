package com.riverfount.booksmanager.catalog.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/** The asset code the library suggests for the next copy (RN02). It is a suggestion: nothing is reserved. */
record NextAssetCodeResponse(
        @Schema(requiredMode = REQUIRED, example = "PAT-00006",
                description = "Código de patrimônio sugerido: o prefixo da biblioteca, um hífen e o próximo número, "
                        + "com pelo menos 5 dígitos")
        String assetCode) {
}
