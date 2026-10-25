package com.riverfount.booksmanager.catalog.application.service;

import com.riverfount.booksmanager.audit.application.port.in.AuditEvent;
import com.riverfount.booksmanager.audit.application.port.in.RecordAuditEventUseCase;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.catalog.application.port.in.DeactivateAuthorUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.GetAuthorUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ListAuthorsQuery;
import com.riverfount.booksmanager.catalog.application.port.in.ListAuthorsUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterAuthorCommand;
import com.riverfount.booksmanager.catalog.application.port.in.ReactivateAuthorUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterAuthorUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateAuthorCommand;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateAuthorUseCase;
import com.riverfount.booksmanager.catalog.application.port.out.AuthorRepository;
import com.riverfount.booksmanager.catalog.domain.Author;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates registering, querying, listing, updating and deactivating Author: calls the domain and
 * persists. A name that another active author already has is refused unless the homonym is confirmed
 * (allowDuplicate): two real authors can share a name, but a duplicate by mistake is likely (RF02). Inactive
 * authors do not count. The check is made at registration time, without a database constraint, so two requests at
 * the same time can both pass; that only creates a homonym, which the rule allows anyway.
 */
@Service
@Transactional
class AuthorService implements RegisterAuthorUseCase, GetAuthorUseCase, ListAuthorsUseCase,
        UpdateAuthorUseCase, DeactivateAuthorUseCase, ReactivateAuthorUseCase {

    private final AuthorRepository authorRepository;
    private final RecordAuditEventUseCase audit;

    AuthorService(AuthorRepository authorRepository, RecordAuditEventUseCase audit) {
        this.authorRepository = authorRepository;
        this.audit = audit;
    }

    @Override
    public Author register(RegisterAuthorCommand command) {
        var author = Author.create(command.name());
        Author.validateNameNotTaken(command.name(), activeAuthorWithName(command.name(), null), command.allowDuplicate());
        return authorRepository.save(author);
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
            // decided before the author changes, so a refusal leaves the loaded author as it was
            Author.validateNameNotTaken(command.name(), activeAuthorWithName(command.name(), author.getId()),
                    command.allowDuplicate());
            author.rename(command.name());
        }
        return authorRepository.save(author);
    }

    @Override
    public void deactivate(Long id) {
        var author = loadExisting(id);
        var wasActive = author.isActive();
        author.deactivate();
        authorRepository.save(author);
        if (wasActive) {
            audit.record(new AuditEvent(AuditAction.AUTHOR_DEACTIVATED, AuditTarget.AUTHOR, id, author.getName()));
        }
    }

    @Override
    public Author reactivate(Long id, boolean allowDuplicate) {
        var author = loadExisting(id);
        if (!author.isActive()) {
            // an author that is already active is not checked: nothing changes for it
            Author.validateNameNotTaken(author.getName(), activeAuthorWithName(author.getName(), author.getId()),
                    allowDuplicate);
        }
        var wasActive = author.isActive();
        author.reactivate();
        var saved = authorRepository.save(author);
        if (!wasActive) {
            audit.record(new AuditEvent(AuditAction.AUTHOR_REACTIVATED, AuditTarget.AUTHOR, id, saved.getName()));
        }
        return saved;
    }

    /** The id of an active author, other than {@code exceptId}, that has the name (ignoring case), or null. */
    private Long activeAuthorWithName(String name, Long exceptId) {
        return authorRepository.findActiveByNames(Set.of(name)).stream()
                .map(Author::getId)
                .filter(id -> !id.equals(exceptId))
                .findFirst()
                .orElse(null);
    }

    private Author loadExisting(Long id) {
        return authorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("autor não encontrado: " + id));
    }
}
