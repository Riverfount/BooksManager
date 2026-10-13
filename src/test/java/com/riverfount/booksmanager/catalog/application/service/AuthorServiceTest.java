package com.riverfount.booksmanager.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.catalog.application.port.in.ListAuthorsQuery;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterAuthorCommand;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateAuthorCommand;
import com.riverfount.booksmanager.catalog.domain.Author;
import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthorServiceTest {

    private InMemoryAuthorRepository repository;
    private AuthorService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAuthorRepository();
        service = new AuthorService(repository);
    }

    @Test
    void shouldRegisterNewAuthorWithId() {
        var author = service.register(new RegisterAuthorCommand("Machado de Assis"));

        assertThat(author.getId()).isNotNull();
        assertThat(author.getName()).isEqualTo("Machado de Assis");
        assertThat(author.isActive()).isTrue();
    }

    @Test
    void shouldAcceptTwoAuthorsWithTheSameName() {
        var first = service.register(new RegisterAuthorCommand("José Silva"));
        var second = service.register(new RegisterAuthorCommand("José Silva"));

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void shouldFindExistingAuthorById() {
        var registered = service.register(new RegisterAuthorCommand("Machado de Assis"));

        var found = service.findById(registered.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Machado de Assis");
    }

    @Test
    void shouldReturnEmptyWhenFindingNonExistentId() {
        assertThat(service.findById(-1L)).isEmpty();
    }

    @Test
    void shouldRejectNullIdWhenFinding() {
        assertThatThrownBy(() -> service.findById(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldListOnlyActiveAuthorsByDefault() {
        service.register(new RegisterAuthorCommand("Machado de Assis"));
        deactivated("Autor Inativo");

        var result = service.list(new ListAuthorsQuery(null, true, PageQuery.of(0, 20, "name")));

        assertThat(result.items()).extracting(Author::getName).containsExactly("Machado de Assis");
    }

    @Test
    void shouldListInactiveAuthorsWhenRequested() {
        service.register(new RegisterAuthorCommand("Machado de Assis"));
        deactivated("Autor Inativo");

        var result = service.list(new ListAuthorsQuery(null, false, PageQuery.of(0, 20, "name")));

        assertThat(result.items()).extracting(Author::getName).containsExactly("Autor Inativo");
    }

    @Test
    void shouldFilterByNameContainingIgnoringCase() {
        service.register(new RegisterAuthorCommand("Machado de Assis"));
        service.register(new RegisterAuthorCommand("Clarice Lispector"));

        var result = service.list(new ListAuthorsQuery("ASSIS", true, PageQuery.of(0, 20, "name")));

        assertThat(result.items()).extracting(Author::getName).containsExactly("Machado de Assis");
    }

    @Test
    void shouldIgnoreBlankNameFilter() {
        service.register(new RegisterAuthorCommand("Machado de Assis"));
        service.register(new RegisterAuthorCommand("Clarice Lispector"));

        var result = service.list(new ListAuthorsQuery("  ", true, PageQuery.of(0, 20, "name")));

        assertThat(result.totalItems()).isEqualTo(2);
    }

    @Test
    void shouldReturnRequestedPageWithPaginationMetadata() {
        service.register(new RegisterAuthorCommand("Autor A"));
        service.register(new RegisterAuthorCommand("Autor B"));
        service.register(new RegisterAuthorCommand("Autor C"));

        var result = service.list(new ListAuthorsQuery(null, true, PageQuery.of(1, 2, "name")));

        assertThat(result.items()).extracting(Author::getName).containsExactly("Autor C");
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalItems()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(2);
    }

    @Test
    void shouldSortByNameDescending() {
        service.register(new RegisterAuthorCommand("Autor A"));
        service.register(new RegisterAuthorCommand("Autor B"));

        var result = service.list(new ListAuthorsQuery(null, true, PageQuery.of(0, 20, "name,desc")));

        assertThat(result.items()).extracting(Author::getName).containsExactly("Autor B", "Autor A");
    }

    @Test
    void shouldRejectUnknownSortField() {
        assertThatThrownBy(() -> new ListAuthorsQuery(null, true, PageQuery.of(0, 20, "birthDate")))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessageContaining("birthDate");
    }

    @Test
    void shouldRenameExistingAuthorAndPersistTheChange() {
        var registered = service.register(new RegisterAuthorCommand("Machado de Asis"));

        var updated = service.update(new UpdateAuthorCommand(registered.getId(), "Machado de Assis"));

        assertThat(updated.getId()).isEqualTo(registered.getId());
        assertThat(updated.getName()).isEqualTo("Machado de Assis");
        assertThat(repository.findById(registered.getId()).orElseThrow().getName()).isEqualTo("Machado de Assis");
    }

    @Test
    void shouldKeepNameWhenItIsNotSent() {
        var registered = service.register(new RegisterAuthorCommand("Machado de Assis"));

        var updated = service.update(new UpdateAuthorCommand(registered.getId(), null));

        assertThat(updated.getName()).isEqualTo("Machado de Assis");
    }

    @Test
    void shouldKeepActiveFlagWhenUpdating() {
        var author = service.register(new RegisterAuthorCommand("Autor Inativo"));
        author.deactivate();
        repository.save(author);

        var updated = service.update(new UpdateAuthorCommand(author.getId(), "Autor Renomeado"));

        assertThat(updated.isActive()).isFalse();
    }

    @Test
    void shouldRejectUpdateOfNonExistentAuthor() {
        assertThatThrownBy(() -> service.update(new UpdateAuthorCommand(99L, "Qualquer Nome")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void shouldRejectBlankNameWhenUpdating() {
        var registered = service.register(new RegisterAuthorCommand("Machado de Assis"));

        assertThatThrownBy(() -> service.update(new UpdateAuthorCommand(registered.getId(), " ")))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(repository.findById(registered.getId()).orElseThrow().getName()).isEqualTo("Machado de Assis");
    }

    @Test
    void shouldDeactivateExistingAuthorKeepingItStored() {
        var registered = service.register(new RegisterAuthorCommand("Machado de Assis"));

        service.deactivate(registered.getId());

        var stored = repository.findById(registered.getId());
        assertThat(stored).isPresent();
        assertThat(stored.get().isActive()).isFalse();
        assertThat(stored.get().getName()).isEqualTo("Machado de Assis");
    }

    @Test
    void shouldBeIdempotentWhenDeactivatingAnAlreadyInactiveAuthor() {
        var registered = service.register(new RegisterAuthorCommand("Machado de Assis"));
        service.deactivate(registered.getId());

        service.deactivate(registered.getId());

        assertThat(repository.findById(registered.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void shouldRejectDeactivationOfNonExistentAuthor() {
        assertThatThrownBy(() -> service.deactivate(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    private void deactivated(String name) {
        var author = service.register(new RegisterAuthorCommand(name));
        author.deactivate();
        repository.save(author);
    }
}
