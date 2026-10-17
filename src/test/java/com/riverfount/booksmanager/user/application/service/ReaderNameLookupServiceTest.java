package com.riverfount.booksmanager.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.user.domain.Reader;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** What the user module tells the loan module's reports: the names of readers, in one call. */
class ReaderNameLookupServiceTest {

    private InMemoryReaderRepository repository;
    private ReaderNameLookupService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryReaderRepository();
        service = new ReaderNameLookupService(repository);
    }

    private Long saveReader(String name, String document) {
        return repository.save(Reader.create(name, document, "r@example.com", null, LocalDate.of(1990, 5, 10), null,
                LocalDate.of(2026, 10, 15))).getId();
    }

    @Test
    void shouldGiveTheNamesOfSeveralReadersAtOnce() {
        var maria = saveReader("Maria Silva", "111");
        var joao = saveReader("João Souza", "222");
        saveReader("Ana Lima", "333");

        var names = service.findNames(Set.of(maria, joao, 999L));

        assertThat(names).containsOnlyKeys(maria, joao);
        assertThat(names.get(maria)).isEqualTo("Maria Silva");
        assertThat(names.get(joao)).isEqualTo("João Souza");
    }

    @Test
    void shouldGiveNoNamesForNoReaders() {
        assertThat(service.findNames(Set.of())).isEmpty();
    }

    @Test
    void anInactiveReaderShouldKeepItsNameBecauseTheHistoryKeepsIt() {
        var id = saveReader("Maria Silva", "111");
        var reader = repository.findById(id).orElseThrow();
        reader.deactivate();
        repository.save(reader);

        assertThat(service.findNames(Set.of(id))).containsEntry(id, "Maria Silva");
    }
}
