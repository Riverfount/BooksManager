package com.riverfount.booksmanager.shared.domain;

import java.util.Locale;

/**
 * Pagination and sorting requested for a listing, independent of any
 * framework. Every listing is paginated, up to 100 items per page (RNF14).
 * Which sort fields are valid is decided by each listing's query.
 */
public record PageQuery(int page, int size, String sortBy, SortDirection direction) {

    public static final int MAX_SIZE = 100;
    public static final int DEFAULT_SIZE = 20;

    public PageQuery {
        if (page < 0) {
            throw new InvalidQueryException("a página não pode ser negativa");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidQueryException("o tamanho da página deve estar entre 1 e " + MAX_SIZE);
        }
        if (sortBy == null || sortBy.isBlank()) {
            throw new InvalidQueryException("o campo de ordenação é obrigatório");
        }
        if (direction == null) {
            throw new InvalidQueryException("a direção da ordenação é obrigatória");
        }
    }

    /**
     * Builds a query from a sort expression in the {@code field[,asc|desc]}
     * format; the direction defaults to ascending.
     */
    public static PageQuery of(int page, int size, String sort) {
        var parts = sort == null ? new String[0] : sort.split(",", -1);
        if (parts.length < 1 || parts.length > 2) {
            throw new InvalidQueryException("ordenação inválida: " + sort + " (use campo,asc ou campo,desc)");
        }
        var direction = parts.length == 2 ? parseDirection(parts[1]) : SortDirection.ASC;
        return new PageQuery(page, size, parts[0].strip(), direction);
    }

    private static SortDirection parseDirection(String value) {
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "asc" -> SortDirection.ASC;
            case "desc" -> SortDirection.DESC;
            default -> throw new InvalidQueryException(
                    "direção de ordenação inválida: " + value + " (use asc ou desc)");
        };
    }
}
