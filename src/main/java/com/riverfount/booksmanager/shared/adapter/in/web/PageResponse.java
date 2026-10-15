package com.riverfount.booksmanager.shared.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.shared.domain.PageResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;

/**
 * One page of a listing in the API (RNF14), shared by all modules'
 * listings.
 */
public record PageResponse<T>(
        @Schema(requiredMode = REQUIRED, description = "Itens da página")
        List<T> items,
        @Schema(requiredMode = REQUIRED, description = "Número da página, a partir de 0", example = "0")
        int page,
        @Schema(requiredMode = REQUIRED, description = "Tamanho da página (máximo de 100)", example = "20")
        int size,
        @Schema(requiredMode = REQUIRED, description = "Total de itens em todas as páginas", example = "42")
        long totalItems,
        @Schema(requiredMode = REQUIRED, description = "Total de páginas", example = "3")
        int totalPages) {

    public static <D, T> PageResponse<T> from(PageResult<D> result, Function<? super D, ? extends T> mapper) {
        PageResult<T> mapped = result.map(mapper);
        return new PageResponse<>(mapped.items(), mapped.page(), mapped.size(), mapped.totalItems(),
                mapped.totalPages());
    }
}
