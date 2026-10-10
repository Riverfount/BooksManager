package com.riverfount.booksmanager.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.catalog.application.port.in.RegisterAuthorCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthorServiceTest {

    private AuthorService service;

    @BeforeEach
    void setUp() {
        service = new AuthorService(new InMemoryAuthorRepository());
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
}
