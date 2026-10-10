package com.riverfount.booksmanager.catalog.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.GetAuthorUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterAuthorCommand;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterAuthorUseCase;
import com.riverfount.booksmanager.catalog.application.port.out.AuthorRepository;
import com.riverfount.booksmanager.catalog.domain.Author;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates registering and querying Author: calls the domain and
 * persists. No unique-name check — author.name has no such restriction.
 */
@Service
@Transactional
class AuthorService implements RegisterAuthorUseCase, GetAuthorUseCase {

    private final AuthorRepository authorRepository;

    AuthorService(AuthorRepository authorRepository) {
        this.authorRepository = authorRepository;
    }

    @Override
    public Author register(RegisterAuthorCommand command) {
        return authorRepository.save(Author.create(command.name()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Author> findById(Long id) {
        return authorRepository.findById(id);
    }
}
