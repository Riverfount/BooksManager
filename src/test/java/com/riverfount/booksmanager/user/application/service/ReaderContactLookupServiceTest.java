package com.riverfount.booksmanager.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.user.domain.Reader;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** RF28: what the user module tells the loan module to notify readers: the name and the e-mail, in one call. */
class ReaderContactLookupServiceTest {

    private InMemoryReaderRepository repository;
    private ReaderContactLookupService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryReaderRepository();
        service = new ReaderContactLookupService(repository);
    }

    private Long saveReader(String name, String document, String email) {
        return repository.save(Reader.create(name, document, email, null, LocalDate.of(1990, 5, 10), null,
                LocalDate.of(2026, 10, 15))).getId();
    }

    @Test
    void shouldGiveTheNameAndTheEmailOfSeveralReadersAtOnce() {
        var maria = saveReader("Maria Silva", "111", "maria@example.com");
        var joao = saveReader("João Souza", "222", "joao@example.com");
        saveReader("Ana Lima", "333", "ana@example.com");

        var contacts = service.findContacts(Set.of(maria, joao, 999L));

        assertThat(contacts).containsOnlyKeys(maria, joao);
        assertThat(contacts.get(maria).name()).isEqualTo("Maria Silva");
        assertThat(contacts.get(maria).email()).isEqualTo("maria@example.com");
        assertThat(contacts.get(joao).email()).isEqualTo("joao@example.com");
    }

    @Test
    void shouldGiveNoContactsForNoReaders() {
        assertThat(service.findContacts(Set.of())).isEmpty();
    }

    @Test
    void anInactiveReaderShouldKeepItsContactBecauseItsLoanGoesOn() {
        var id = saveReader("Maria Silva", "111", "maria@example.com");
        var reader = repository.findById(id).orElseThrow();
        reader.deactivate();
        repository.save(reader);

        assertThat(service.findContacts(Set.of(id))).containsKey(id);
    }
}
