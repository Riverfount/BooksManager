package com.riverfount.booksmanager.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.catalog.application.port.in.ListBooksQuery;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterBookCommand;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateBookCommand;
import com.riverfount.booksmanager.catalog.domain.Book;
import com.riverfount.booksmanager.catalog.domain.Author;
import com.riverfount.booksmanager.catalog.domain.Category;
import com.riverfount.booksmanager.catalog.domain.Isbn;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);

    private BookService service;
    private InMemoryBookRepository bookRepository;
    private InMemoryCategoryRepository categoryRepository;
    private InMemoryAuthorRepository authorRepository;
    private Long categoryId;
    private Long authorId;

    @BeforeEach
    void setUp() {
        bookRepository = new InMemoryBookRepository();
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
    void shouldNotRegisterWithCategoryDeactivatedThroughItsUseCase() {
        // HU21: once deactivated, the category can no longer be used in new books
        new CategoryService(categoryRepository).deactivate(categoryId);

        assertThatThrownBy(() -> service.register(new RegisterBookCommand("9788533302273", "Dom Casmurro",
                "Editora X", 1899, categoryId, Set.of(authorId))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("categoria inativa");
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

    @Test
    void shouldListOnlyActiveBooksByDefault() {
        register("9788533302273", "Dom Casmurro", 1899);
        deactivated(register("9788535914849", "Livro Inativo", 2000));

        var result = service.list(new ListBooksQuery(true, PageQuery.of(0, 20, "title")));

        assertThat(result.items()).extracting(Book::getTitle).containsExactly("Dom Casmurro");
    }

    @Test
    void shouldListInactiveBooksWhenRequested() {
        register("9788533302273", "Dom Casmurro", 1899);
        deactivated(register("9788535914849", "Livro Inativo", 2000));

        var result = service.list(new ListBooksQuery(false, PageQuery.of(0, 20, "title")));

        assertThat(result.items()).extracting(Book::getTitle).containsExactly("Livro Inativo");
    }

    @Test
    void shouldReturnRequestedPageWithPaginationMetadata() {
        register("9788533302273", "Livro A", 1899);
        register("9788535914849", "Livro B", 2000);
        register("9788525406958", "Livro C", 2010);

        var result = service.list(new ListBooksQuery(true, PageQuery.of(1, 2, "title")));

        assertThat(result.items()).extracting(Book::getTitle).containsExactly("Livro C");
        assertThat(result.totalItems()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(2);
    }

    @Test
    void shouldSortByPublicationYearDescending() {
        register("9788533302273", "Mais Antigo", 1899);
        register("9788535914849", "Mais Novo", 2010);

        var result = service.list(new ListBooksQuery(true, PageQuery.of(0, 20, "publicationYear,desc")));

        assertThat(result.items()).extracting(Book::getTitle).containsExactly("Mais Novo", "Mais Antigo");
    }

    @Test
    void shouldSortByIsbn() {
        register("9788535914849", "Livro B", 2000);
        register("9788533302273", "Livro A", 1899);

        var result = service.list(new ListBooksQuery(true, PageQuery.of(0, 20, "isbn")));

        assertThat(result.items()).extracting(book -> book.getIsbn().value())
                .containsExactly("9788533302273", "9788535914849");
    }

    @Test
    void shouldRejectUnknownSortField() {
        assertThatThrownBy(() -> new ListBooksQuery(true, PageQuery.of(0, 20, "publisher")))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessageContaining("publisher");
    }

    @Test
    void shouldUpdateOnlyTheSentFields() {
        var book = register("9788533302273", "Dom Casmurro", 1899);

        var updated = service.update(new UpdateBookCommand(book.getId(), "Dom Casmurro (revisado)", null, null,
                null, null));

        assertThat(updated.getTitle()).isEqualTo("Dom Casmurro (revisado)");
        assertThat(updated.getPublisher()).isEqualTo("Editora X");
        assertThat(updated.getPublicationYear()).isEqualTo(1899);
        assertThat(updated.getCategoryId()).isEqualTo(categoryId);
        assertThat(updated.getAuthorIds()).containsExactly(authorId);
        assertThat(updated.getIsbn()).isEqualTo(new Isbn("9788533302273"));
        assertThat(bookRepository.findById(book.getId()).orElseThrow().getTitle())
                .isEqualTo("Dom Casmurro (revisado)");
    }

    @Test
    void shouldUpdateAllEditableFields() {
        var book = register("9788533302273", "Dom Casmurro", 1899);
        var newCategoryId = categoryRepository.save(Category.create("Romance")).getId();
        var newAuthorId = authorRepository.save(Author.create("Outro Autor")).getId();

        var updated = service.update(new UpdateBookCommand(book.getId(), "Novo título", "Editora Y", 1900,
                newCategoryId, Set.of(newAuthorId)));

        assertThat(updated.getTitle()).isEqualTo("Novo título");
        assertThat(updated.getPublisher()).isEqualTo("Editora Y");
        assertThat(updated.getPublicationYear()).isEqualTo(1900);
        assertThat(updated.getCategoryId()).isEqualTo(newCategoryId);
        assertThat(updated.getAuthorIds()).containsExactly(newAuthorId);
    }

    @Test
    void shouldNotUpdateNonExistentBook() {
        assertThatThrownBy(() -> service.update(new UpdateBookCommand(999L, "Título", null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldNotUpdateWithNonExistentCategory() {
        var book = register("9788533302273", "Dom Casmurro", 1899);

        assertThatThrownBy(() -> service.update(new UpdateBookCommand(book.getId(), null, null, null, 999999L,
                null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("categoria não encontrada");
    }

    @Test
    void shouldNotUpdateWithInactiveCategory() {
        var book = register("9788533302273", "Dom Casmurro", 1899);
        categoryRepository.save(Category.reconstitute(500L, "Categoria inativa", false));

        assertThatThrownBy(() -> service.update(new UpdateBookCommand(book.getId(), null, null, null, 500L,
                null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("categoria inativa");
    }

    @Test
    void shouldNotUpdateWithNonExistentAuthor() {
        var book = register("9788533302273", "Dom Casmurro", 1899);

        assertThatThrownBy(() -> service.update(new UpdateBookCommand(book.getId(), null, null, null, null,
                Set.of(authorId, 999999L))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("autor não encontrado");
    }

    @Test
    void shouldNotUpdateWithInactiveAuthor() {
        var book = register("9788533302273", "Dom Casmurro", 1899);
        authorRepository.save(Author.reconstitute(600L, "Autor inativo", false));

        assertThatThrownBy(() -> service.update(new UpdateBookCommand(book.getId(), null, null, null, null,
                Set.of(600L))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("autor inativo");
    }

    @Test
    void shouldKeepCurrentInactiveCategoryWhenItIsNotChanged() {
        var book = register("9788533302273", "Dom Casmurro", 1899);
        categoryRepository.save(Category.reconstitute(categoryId, "Ficção", false));

        // sending the same categoryId the book already had is not a change
        var updated = service.update(new UpdateBookCommand(book.getId(), "Novo título", null, null, categoryId,
                null));

        assertThat(updated.getTitle()).isEqualTo("Novo título");
        assertThat(updated.getCategoryId()).isEqualTo(categoryId);
    }

    @Test
    void shouldKeepCurrentInactiveAuthorWhenItIsNotRemoved() {
        var book = register("9788533302273", "Dom Casmurro", 1899);
        authorRepository.save(Author.reconstitute(authorId, "Machado de Assis", false));
        var newAuthorId = authorRepository.save(Author.create("Outro Autor")).getId();

        var updated = service.update(new UpdateBookCommand(book.getId(), null, null, null, null,
                Set.of(authorId, newAuthorId)));

        assertThat(updated.getAuthorIds()).containsExactlyInAnyOrder(authorId, newAuthorId);
    }

    @Test
    void shouldNotUpdateWithInvalidTitle() {
        var book = register("9788533302273", "Dom Casmurro", 1899);

        assertThatThrownBy(() -> service.update(new UpdateBookCommand(book.getId(), " ", null, null, null,
                null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    private Book register(String isbn, String title, Integer publicationYear) {
        return service.register(new RegisterBookCommand(isbn, title, "Editora X", publicationYear, categoryId,
                Set.of(authorId)));
    }

    private void deactivated(Book book) {
        book.deactivate();
        bookRepository.save(book);
    }
}
