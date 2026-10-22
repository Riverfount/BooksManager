package com.riverfount.booksmanager.loan.adapter.in.web;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.riverfount.booksmanager.loan.application.port.in.MostBorrowedBook;
import io.swagger.v3.oas.annotations.media.Schema;

record MostBorrowedBookResponse(
        @Schema(requiredMode = REQUIRED, description = "Id do livro", example = "3")
        Long bookId,
        @Schema(description = "Título do livro", example = "Dom Casmurro")
        String title,
        @Schema(requiredMode = REQUIRED, description = "Total de empréstimos do livro no período (soma dos seus "
                + "exemplares); a renovação não conta como novo empréstimo", example = "12")
        long totalLoans) {

    static MostBorrowedBookResponse from(MostBorrowedBook book) {
        return new MostBorrowedBookResponse(book.bookId(), book.title(), book.totalLoans());
    }
}
