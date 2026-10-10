package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.domain.Book;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

record BookResponse(
        @Schema(description = "Identificador do livro", example = "1")
        Long id,
        @Schema(description = "ISBN-13, sem hífens", example = "9788533302273")
        String isbn,
        @Schema(description = "Título do livro", example = "Dom Casmurro")
        String title,
        @Schema(description = "Editora", example = "Garnier")
        String publisher,
        @Schema(description = "Ano de publicação", example = "1899")
        Integer publicationYear,
        @Schema(description = "Id da categoria", example = "1")
        Long categoryId,
        @Schema(description = "Ids dos autores", example = "[1]")
        Set<Long> authorIds,
        @Schema(description = "Se o livro está ativo", example = "true")
        boolean active) {

    static BookResponse from(Book book) {
        return new BookResponse(book.getId(), book.getIsbn().value(), book.getTitle(), book.getPublisher(),
                book.getPublicationYear(), book.getCategoryId(), book.getAuthorIds(), book.isActive());
    }
}
