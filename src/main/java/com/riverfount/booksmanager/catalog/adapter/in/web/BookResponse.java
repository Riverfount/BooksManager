package com.riverfount.booksmanager.catalog.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.catalog.application.port.in.BookWithAvailability;
import com.riverfount.booksmanager.catalog.domain.Book;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;

record BookResponse(
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
        @Schema(requiredMode = REQUIRED, description = "Id da categoria", example = "1")
        Long categoryId,
        @Schema(requiredMode = REQUIRED, description = "Ids dos autores", example = "[1]")
        Set<Long> authorIds,
        @Schema(requiredMode = REQUIRED, description = "Se o livro está ativo", example = "true")
        boolean active,
        @Schema(requiredMode = REQUIRED, example = "2",
                description = "Quantos exemplares estão disponíveis para empréstimo (ativos e com status "
                        + "AVAILABLE)")
        long availableCopies,
        @Schema(requiredMode = REQUIRED, example = "5",
                description = "Quantos exemplares ativos o livro tem, em qualquer status")
        long totalCopies,
        @Schema(requiredMode = REQUIRED, description = "Categoria do livro, com o nome; vem também quando "
                + "inativa (o histórico a mantém)")
        CategorySummary category,
        @Schema(requiredMode = REQUIRED, description = "Autores do livro, com o nome, em ordem de nome; vêm "
                + "também quando inativos")
        List<AuthorSummary> authors) {

    static BookResponse from(BookWithAvailability item) {
        Book book = item.book();
        return new BookResponse(book.getId(), book.getIsbn().value(), book.getTitle(), book.getPublisher(),
                book.getPublicationYear(), book.getCategoryId(), book.getAuthorIds(), book.isActive(),
                item.availableCopies(), item.totalCopies(), CategorySummary.from(item.category()),
                item.authors().stream().map(AuthorSummary::from).toList());
    }
}
