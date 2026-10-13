package com.riverfount.booksmanager.catalog.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.DeactivateAuthorUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.GetAuthorUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ListAuthorsQuery;
import com.riverfount.booksmanager.catalog.application.port.in.ListAuthorsUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterAuthorCommand;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterAuthorUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateAuthorCommand;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateAuthorUseCase;
import com.riverfount.booksmanager.catalog.application.port.out.AuthorRepository;
import com.riverfount.booksmanager.catalog.domain.Author;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates registering, querying, listing, updating and deactivating Author: calls the domain and
 * persists. No unique-name check — author.name has no such restriction.
 */
@Service
@Transactional
class AuthorService implements RegisterAuthorUseCase, GetAuthorUseCase, ListAuthorsUseCase,
        UpdateAuthorUseCase, DeactivateAuthorUseCase {

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

    @Override
    @Transactional(readOnly = true)
    public PageResult<Author> list(ListAuthorsQuery query) {
        return authorRepository.findAll(query.name(), query.active(), query.page());
    }

    @Override
    public Author update(UpdateAuthorCommand command) {
        var author = loadExisting(command.id());
        if (command.name() != null) {
            author.rename(command.name());
        }
        return authorRepository.save(author);
    }

    @Override
    public void deactivate(Long id) {
        var author = loadExisting(id);
        author.deactivate();
        authorRepository.save(author);
    }

    private Author loadExisting(Long id) {
        return authorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("autor não encontrado: " + id));
    }
}
