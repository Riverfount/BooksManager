package com.riverfount.booksmanager.catalog.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.catalog.application.port.in.BookWithAvailability;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A book as the reader sees it (Phase 6): what is needed to find it and to know whether it can be borrowed now.
 * There is nothing about the copies themselves (their codes, their states, how many there are in all) nor about the
 * registry (whether it is active, the ids of the category and of the authors beside their names).
 */
record ReaderBookResponse(
        @Schema(requiredMode = REQUIRED, description = "Identificador do livro", example = "1")
        Long id,
        @Schema(requiredMode = REQUIRED, description = "ISBN-13, sem hífens", example = "9788533302273")
        String isbn,
        @Schema(requiredMode = REQUIRED, description = "Título do livro", example = "Dom Casmurro")
        String title,
        @Schema(description = "Editora (só vem vazia em livro cadastrado antes de ela ser obrigatória)", example = "Garnier")
        String publisher,
        @Schema(description = "Ano de publicação (só vem vazio em livro cadastrado antes de ele ser obrigatório)", example = "1899")
        Integer publicationYear,
        @Schema(requiredMode = REQUIRED, example = "2",
                description = "Quantos exemplares estão disponíveis para empréstimo agora; zero quer dizer que o "
                        + "livro pode ser reservado")
        long availableCopies,
        @Schema(requiredMode = REQUIRED, description = "Categoria do livro")
        CategorySummary category,
        @Schema(requiredMode = REQUIRED, description = "Autores do livro, em ordem de nome")
        List<AuthorSummary> authors) {

    static ReaderBookResponse from(BookWithAvailability item) {
        var book = item.book();
        return new ReaderBookResponse(book.getId(), book.getIsbn().value(), book.getTitle(), book.getPublisher(),
                book.getPublicationYear(), item.availableCopies(), CategorySummary.from(item.category()),
                item.authors().stream().map(AuthorSummary::from).toList());
    }
}
