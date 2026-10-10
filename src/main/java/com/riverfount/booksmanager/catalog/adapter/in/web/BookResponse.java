package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.domain.Book;
import java.util.Set;

record BookResponse(Long id, String isbn, String title, String publisher, Integer publicationYear,
        Long categoryId, Set<Long> authorIds, boolean active) {

    static BookResponse from(Book book) {
        return new BookResponse(book.getId(), book.getIsbn().value(), book.getTitle(), book.getPublisher(),
                book.getPublicationYear(), book.getCategoryId(), book.getAuthorIds(), book.isActive());
    }
}
