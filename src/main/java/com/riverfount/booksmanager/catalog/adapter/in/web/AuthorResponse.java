package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.domain.Author;

record AuthorResponse(Long id, String name, boolean active) {

    static AuthorResponse from(Author author) {
        return new AuthorResponse(author.getId(), author.getName(), author.isActive());
    }
}
