package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.domain.CopyStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Partial update of a copy: an omitted (or null) status keeps the current
 * one. There are no assetCode or bookId fields (immutable): if sent, they
 * are ignored.
 */
record UpdateCopyRequest(
        @Schema(description = "Novo status (opcional). Só DAMAGED, LOST e AVAILABLE (a partir de DAMAGED "
                + "ou LOST) são aceitos; BORROWED e RESERVED mudam pelos fluxos de empréstimo e reserva.",
                example = "DAMAGED", allowableValues = {"AVAILABLE", "DAMAGED", "LOST"})
        CopyStatus status) {
}
