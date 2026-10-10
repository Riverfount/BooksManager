package com.riverfount.booksmanager.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.catalog.application.port.in.RegisterCopyCommand;
import com.riverfount.booksmanager.catalog.domain.Book;
import com.riverfount.booksmanager.catalog.domain.Isbn;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CopyServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);

    private CopyService service;
    private InMemoryBookRepository bookRepository;
    private Long bookId;

    @BeforeEach
    void setUp() {
        var copyRepository = new InMemoryCopyRepository();
        bookRepository = new InMemoryBookRepository();
        service = new CopyService(copyRepository, bookRepository);

        var categoryId = 1L;
        var authorId = 1L;
        var book = Book.create(new Isbn("9788533302273"), "Dom Casmurro", "Editora X", 1899, categoryId,
                Set.of(authorId), FIXED_CLOCK);
        bookId = bookRepository.save(book).getId();
    }

    @Test
    void shouldRegisterCopyWithValidData() {
        var copy = service.register(new RegisterCopyCommand(bookId, "PAT-1"));

        assertThat(copy.getId()).isNotNull();
        assertThat(copy.getBookId()).isEqualTo(bookId);
        assertThat(copy.getAssetCode()).isEqualTo("PAT-1");
    }

    @Test
    void shouldNotRegisterWithNonExistentBook() {
        assertThatThrownBy(() -> service.register(new RegisterCopyCommand(999999L, "PAT-1")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotRegisterWithInactiveBook() {
        var inactiveBookId = 500L;
        var inactiveBook = Book.reconstitute(inactiveBookId, new Isbn("080442957X"), "Outro Livro", "Editora Y",
                1900, 1L, Set.of(1L), false);
        bookRepository.save(inactiveBook);

        assertThatThrownBy(() -> service.register(new RegisterCopyCommand(inactiveBookId, "PAT-1")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotRegisterWithDuplicateAssetCode() {
        service.register(new RegisterCopyCommand(bookId, "PAT-1"));

        assertThatThrownBy(() -> service.register(new RegisterCopyCommand(bookId, "PAT-1")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldFindExistingCopyById() {
        var registered = service.register(new RegisterCopyCommand(bookId, "PAT-1"));

        var found = service.findById(registered.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getAssetCode()).isEqualTo("PAT-1");
    }

    @Test
    void shouldReturnEmptyWhenFindingNonExistentId() {
        assertThat(service.findById(-1L)).isEmpty();
    }
}
