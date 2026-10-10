package com.riverfount.booksmanager.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.catalog.application.port.in.RegisterBookCommand;
import com.riverfount.booksmanager.catalog.domain.Author;
import com.riverfount.booksmanager.catalog.domain.Category;
import com.riverfount.booksmanager.catalog.domain.Isbn;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);

    private BookService service;
    private InMemoryCategoryRepository categoryRepository;
    private InMemoryAuthorRepository authorRepository;
    private Long categoryId;
    private Long authorId;

    @BeforeEach
    void setUp() {
        var bookRepository = new InMemoryBookRepository();
        categoryRepository = new InMemoryCategoryRepository();
        authorRepository = new InMemoryAuthorRepository();
        service = new BookService(bookRepository, categoryRepository, authorRepository, FIXED_CLOCK);

        categoryId = categoryRepository.save(Category.create("Ficção")).getId();
        authorId = authorRepository.save(Author.create("Machado de Assis")).getId();
    }

    @Test
    void shouldRegisterBookWithValidData() {
        var book = service.register(new RegisterBookCommand("9788533302273", "Dom Casmurro", "Editora X", 1899,
                categoryId, Set.of(authorId)));

        assertThat(book.getId()).isNotNull();
        assertThat(book.getIsbn()).isEqualTo(new Isbn("9788533302273"));
        assertThat(book.getTitle()).isEqualTo("Dom Casmurro");
        assertThat(book.getCategoryId()).isEqualTo(categoryId);
        assertThat(book.getAuthorIds()).containsExactly(authorId);
    }

    @Test
    void shouldNotRegisterWithNonExistentCategory() {
        assertThatThrownBy(() -> service.register(new RegisterBookCommand("9788533302273", "Dom Casmurro",
                "Editora X", 1899, 999999L, Set.of(authorId))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotRegisterWithNonExistentAuthor() {
        assertThatThrownBy(() -> service.register(new RegisterBookCommand("9788533302273", "Dom Casmurro",
                "Editora X", 1899, categoryId, Set.of(999999L))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotRegisterWithInactiveCategory() {
        var inactiveCategoryId = 500L;
        categoryRepository.save(Category.reconstitute(inactiveCategoryId, "Categoria inativa", false));

        assertThatThrownBy(() -> service.register(new RegisterBookCommand("9788533302273", "Dom Casmurro",
                "Editora X", 1899, inactiveCategoryId, Set.of(authorId))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotRegisterWithInactiveAuthor() {
        var inactiveAuthorId = 500L;
        authorRepository.save(Author.reconstitute(inactiveAuthorId, "Autor inativo", false));

        assertThatThrownBy(() -> service.register(new RegisterBookCommand("9788533302273", "Dom Casmurro",
                "Editora X", 1899, categoryId, Set.of(inactiveAuthorId))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotRegisterWithDuplicateIsbn() {
        service.register(new RegisterBookCommand("9788533302273", "Dom Casmurro", "Editora X", 1899, categoryId,
                Set.of(authorId)));

        assertThatThrownBy(() -> service.register(new RegisterBookCommand("9788533302273", "Outro Livro",
                "Editora Y", 1900, categoryId, Set.of(authorId))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotRegisterWithFuturePublicationYear() {
        assertThatThrownBy(() -> service.register(new RegisterBookCommand("9788533302273", "Dom Casmurro",
                "Editora X", 2027, categoryId, Set.of(authorId))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldFindExistingBookById() {
        var registered = service.register(new RegisterBookCommand("9788533302273", "Dom Casmurro", "Editora X",
                1899, categoryId, Set.of(authorId)));

        var found = service.findById(registered.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Dom Casmurro");
    }

    @Test
    void shouldReturnEmptyWhenFindingNonExistentId() {
        assertThat(service.findById(-1L)).isEmpty();
    }
}
