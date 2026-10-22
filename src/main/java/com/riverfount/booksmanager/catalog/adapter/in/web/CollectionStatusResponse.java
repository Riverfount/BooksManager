package com.riverfount.booksmanager.catalog.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.catalog.application.port.in.CollectionStatus;
import com.riverfount.booksmanager.catalog.domain.CopyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

record CollectionStatusResponse(
        @Schema(requiredMode = REQUIRED, description = "Exemplares ativos, no total; é a soma de "
                + "`activeByStatus`", example = "120")
        long totalCopies,
        @Schema(requiredMode = REQUIRED, description = "Exemplares ativos por status (AVAILABLE, BORROWED, "
                + "RESERVED, DAMAGED e LOST); um status sem exemplares vem com zero",
                example = "{\"AVAILABLE\": 90, \"BORROWED\": 20, \"RESERVED\": 4, \"DAMAGED\": 3, \"LOST\": 3}")
        Map<CopyStatus, Long> activeByStatus,
        @Schema(requiredMode = REQUIRED, description = "Exemplares inativos, à parte, qualquer que seja o status; "
                + "não entram na contagem dos ativos", example = "5")
        long inactiveCopies) {

    static CollectionStatusResponse from(CollectionStatus status) {
        return new CollectionStatusResponse(status.totalActive(), status.activeByStatus(), status.inactive());
    }
}
