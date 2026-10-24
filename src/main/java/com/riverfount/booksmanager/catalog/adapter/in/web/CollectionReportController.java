package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.application.port.in.GetCollectionStatusUseCase;
import com.riverfount.booksmanager.shared.adapter.in.web.AdminOrOperator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AdminOrOperator
@RestController
@RequestMapping("/api/v1/reports/collection-status")
@Tag(name = "Reports", description = "Relatórios da biblioteca (RF23).")
class CollectionReportController {

    private final GetCollectionStatusUseCase getCollectionStatusUseCase;

    CollectionReportController(GetCollectionStatusUseCase getCollectionStatusUseCase) {
        this.getCollectionStatusUseCase = getCollectionStatusUseCase;
    }

    @GetMapping
    @Operation(summary = "Situação do acervo",
            description = "Devolve, numa chamada só, o total de exemplares ativos e quantos há em cada status "
                    + "(`AVAILABLE`, `BORROWED`, `RESERVED`, `DAMAGED` e `LOST`; um status sem exemplares vem com "
                    + "zero, então a soma por status é sempre o total). Os exemplares inativos (RN14) vêm à "
                    + "parte, em `inactiveCopies`, e não entram na contagem dos ativos.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Situação do acervo"))
    CollectionStatusResponse get() {
        return CollectionStatusResponse.from(getCollectionStatusUseCase.get());
    }
}
