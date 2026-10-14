package com.riverfount.booksmanager.catalog.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.catalog.application.port.in.BookWithAvailability;
import com.riverfount.booksmanager.catalog.domain.Book;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

record BookResponse(
        @Schema(requiredMode = REQUIRED, description = "Identificador do livro", example = "1")
        Long id,
        @Schema(requiredMode = REQUIRED, description = "ISBN-13, sem hífens", example = "9788533302273")
        String isbn,
        @Schema(requiredMode = REQUIRED, description = "Título do livro", example = "Dom Casmurro")
        String title,
        @Schema(description = "Editora", example = "Garnier")
        String publisher,
        @Schema(description = "Ano de publicação", example = "1899")
        Integer publicationYear,
        @Schema(requiredMode = REQUIRED, description = "Id da categoria", example = "1")
        Long categoryId,
        @Schema(requiredMode = REQUIRED, description = "Ids dos autores", example = "[1]")
        Set<Long> authorIds,
        @Schema(requiredMode = REQUIRED, description = "Se o livro está ativo", example = "true")
        boolean active,
        @Schema(requiredMode = REQUIRED, example = "2",
                description = "Quantos exemplares estão disponíveis para empréstimo (ativos e com status "
                        + "AVAILABLE)")
        long availableCopies) {

    static BookResponse from(BookWithAvailability item) {
        Book book = item.book();
        return new BookResponse(book.getId(), book.getIsbn().value(), book.getTitle(), book.getPublisher(),
                book.getPublicationYear(), book.getCategoryId(), book.getAuthorIds(), book.isActive(),
                item.availableCopies());
    }
}
