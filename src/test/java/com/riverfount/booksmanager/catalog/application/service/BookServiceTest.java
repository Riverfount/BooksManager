package com.riverfount.booksmanager.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.catalog.application.port.in.ListBooksQuery;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterBookCommand;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateBookCommand;
import com.riverfount.booksmanager.catalog.domain.Book;
import com.riverfount.booksmanager.catalog.domain.Author;
import com.riverfount.booksmanager.catalog.domain.Category;
import com.riverfount.booksmanager.catalog.domain.Copy;
import com.riverfount.booksmanager.catalog.domain.CopyStatus;
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
    private InMemoryCopyRepository copyRepository;
    private Long categoryId;
    private Long authorId;

    @BeforeEach
    void setUp() {
        authorRepository = new InMemoryAuthorRepository();
        bookRepository = new InMemoryBookRepository(authorRepository);
        categoryRepository = new InMemoryCategoryRepository();
        copyRepository = new InMemoryCopyRepository();
        service = new BookService(bookRepository, categoryRepository, authorRepository, copyRepository,
                FIXED_CLOCK);

        categoryId = categoryRepository.save(Category.create("Ficção")).getId();
        authorId = authorRepository.save(Author.create("Machado de Assis")).getId();
    }

    @Test
    void shouldRegisterBookWithValidData() {
        var registered = service.register(new RegisterBookCommand("9788533302273", "Dom Casmurro", "Editora X",
                1899, categoryId, Set.of(authorId)));
        var book = registered.book();

        assertThat(registered.availableCopies()).isZero();
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

        var found = service.findById(registered.book().getId());

        assertThat(found).isPresent();
        assertThat(found.get().book().getTitle()).isEqualTo("Dom Casmurro");
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

        assertThat(result.items()).extracting(item -> item.book().getTitle()).containsExactly("Dom Casmurro");
    }

    @Test
    void shouldListInactiveBooksWhenRequested() {
        register("9788533302273", "Dom Casmurro", 1899);
        deactivated(register("9788535914849", "Livro Inativo", 2000));

        var result = service.list(new ListBooksQuery(false, PageQuery.of(0, 20, "title")));

        assertThat(result.items()).extracting(item -> item.book().getTitle()).containsExactly("Livro Inativo");
    }

    @Test
    void shouldReturnRequestedPageWithPaginationMetadata() {
        register("9788533302273", "Livro A", 1899);
        register("9788535914849", "Livro B", 2000);
        register("9788525406958", "Livro C", 2010);

        var result = service.list(new ListBooksQuery(true, PageQuery.of(1, 2, "title")));

        assertThat(result.items()).extracting(item -> item.book().getTitle()).containsExactly("Livro C");
        assertThat(result.totalItems()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(2);
    }

    @Test
    void shouldSortByPublicationYearDescending() {
        register("9788533302273", "Mais Antigo", 1899);
        register("9788535914849", "Mais Novo", 2010);

        var result = service.list(new ListBooksQuery(true, PageQuery.of(0, 20, "publicationYear,desc")));

        assertThat(result.items()).extracting(item -> item.book().getTitle()).containsExactly("Mais Novo", "Mais Antigo");
    }

    @Test
    void shouldSortByIsbn() {
        register("9788535914849", "Livro B", 2000);
        register("9788533302273", "Livro A", 1899);

        var result = service.list(new ListBooksQuery(true, PageQuery.of(0, 20, "isbn")));

        assertThat(result.items()).extracting(item -> item.book().getIsbn().value())
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

        assertThat(updated.book().getTitle()).isEqualTo("Dom Casmurro (revisado)");
        assertThat(updated.book().getPublisher()).isEqualTo("Editora X");
        assertThat(updated.book().getPublicationYear()).isEqualTo(1899);
        assertThat(updated.book().getCategoryId()).isEqualTo(categoryId);
        assertThat(updated.book().getAuthorIds()).containsExactly(authorId);
        assertThat(updated.book().getIsbn()).isEqualTo(new Isbn("9788533302273"));
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

        assertThat(updated.book().getTitle()).isEqualTo("Novo título");
        assertThat(updated.book().getPublisher()).isEqualTo("Editora Y");
        assertThat(updated.book().getPublicationYear()).isEqualTo(1900);
        assertThat(updated.book().getCategoryId()).isEqualTo(newCategoryId);
        assertThat(updated.book().getAuthorIds()).containsExactly(newAuthorId);
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

        assertThat(updated.book().getTitle()).isEqualTo("Novo título");
        assertThat(updated.book().getCategoryId()).isEqualTo(categoryId);
    }

    @Test
    void shouldKeepCurrentInactiveAuthorWhenItIsNotRemoved() {
        var book = register("9788533302273", "Dom Casmurro", 1899);
        authorRepository.save(Author.reconstitute(authorId, "Machado de Assis", false));
        var newAuthorId = authorRepository.save(Author.create("Outro Autor")).getId();

        var updated = service.update(new UpdateBookCommand(book.getId(), null, null, null, null,
                Set.of(authorId, newAuthorId)));

        assertThat(updated.book().getAuthorIds()).containsExactlyInAnyOrder(authorId, newAuthorId);
    }

    @Test
    void shouldNotUpdateWithInvalidTitle() {
        var book = register("9788533302273", "Dom Casmurro", 1899);

        assertThatThrownBy(() -> service.update(new UpdateBookCommand(book.getId(), " ", null, null, null,
                null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldDeactivateExistingBookKeepingItStored() {
        var book = register("9788533302273", "Dom Casmurro", 1899);

        service.deactivate(book.getId());

        var stored = bookRepository.findById(book.getId()).orElseThrow();
        assertThat(stored.isActive()).isFalse();
        assertThat(stored.getTitle()).isEqualTo("Dom Casmurro");
        assertThat(stored.getAuthorIds()).containsExactly(authorId);
    }

    @Test
    void shouldBeIdempotentWhenDeactivatingAnInactiveBook() {
        var book = register("9788533302273", "Dom Casmurro", 1899);
        deactivated(book);

        service.deactivate(book.getId());

        assertThat(bookRepository.findById(book.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void shouldNotDeactivateNonExistentBook() {
        assertThatThrownBy(() -> service.deactivate(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void shouldFilterByTitleContainingIgnoringCase() {
        register("9788533302273", "Dom Casmurro", 1899);
        register("9788535914849", "Memórias Póstumas", 1881);

        var result = service.list(new ListBooksQuery("CASMUR", null, null, null, true,
                PageQuery.of(0, 20, "title")));

        assertThat(result.items()).extracting(item -> item.book().getTitle()).containsExactly("Dom Casmurro");
    }

    @Test
    void shouldFilterByAuthorNameContainingIgnoringCase() {
        var otherAuthorId = authorRepository.save(Author.create("Clarice Lispector")).getId();
        register("9788533302273", "Dom Casmurro", 1899);
        service.register(new RegisterBookCommand("9788535914849", "A Hora da Estrela", "Editora X", 1977,
                categoryId, Set.of(otherAuthorId)));

        var result = service.list(new ListBooksQuery(null, "LISPEC", null, null, true,
                PageQuery.of(0, 20, "title")));

        assertThat(result.items()).extracting(item -> item.book().getTitle())
                .containsExactly("A Hora da Estrela");
    }

    @Test
    void shouldFilterByPartOfTheIsbnIgnoringHyphens() {
        register("9788533302273", "Dom Casmurro", 1899);
        register("9788535914849", "Memórias Póstumas", 1881);

        var result = service.list(new ListBooksQuery(null, null, "978-85-3330", null, true,
                PageQuery.of(0, 20, "title")));

        assertThat(result.items()).extracting(item -> item.book().getTitle()).containsExactly("Dom Casmurro");
    }

    @Test
    void shouldFilterByCategory() {
        var otherCategoryId = categoryRepository.save(Category.create("Romance")).getId();
        register("9788533302273", "Dom Casmurro", 1899);
        service.register(new RegisterBookCommand("9788535914849", "Memórias Póstumas", "Editora X", 1881,
                otherCategoryId, Set.of(authorId)));

        var result = service.list(new ListBooksQuery(null, null, null, otherCategoryId, true,
                PageQuery.of(0, 20, "title")));

        assertThat(result.items()).extracting(item -> item.book().getTitle())
                .containsExactly("Memórias Póstumas");
    }

    @Test
    void shouldCombineFiltersWithAnd() {
        register("9788533302273", "Dom Casmurro", 1899);
        register("9788535914849", "Dom Quixote", 1605);

        var result = service.list(new ListBooksQuery("dom", null, "9788535", null, true,
                PageQuery.of(0, 20, "title")));

        assertThat(result.items()).extracting(item -> item.book().getTitle()).containsExactly("Dom Quixote");
    }

    @Test
    void shouldIgnoreBlankFilters() {
        register("9788533302273", "Dom Casmurro", 1899);
        register("9788535914849", "Memórias Póstumas", 1881);

        var result = service.list(new ListBooksQuery("  ", "", " ", null, true, PageQuery.of(0, 20, "title")));

        assertThat(result.totalItems()).isEqualTo(2);
    }

    @Test
    void shouldReturnNoBooksWhenNothingMatches() {
        register("9788533302273", "Dom Casmurro", 1899);

        var result = service.list(new ListBooksQuery("inexistente", null, null, null, true,
                PageQuery.of(0, 20, "title")));

        assertThat(result.items()).isEmpty();
        assertThat(result.totalItems()).isZero();
    }

    @Test
    void shouldShowAvailableCopiesOfEachBookInTheList() {
        var domCasmurro = register("9788533302273", "Dom Casmurro", 1899);
        var memorias = register("9788535914849", "Memórias Póstumas", 1881);
        register("9788525406958", "Sem exemplares", 2010);
        copyRepository.save(Copy.create(domCasmurro.getId(), "PAT-1"));
        copyRepository.save(Copy.create(domCasmurro.getId(), "PAT-2"));
        copyRepository.save(Copy.create(memorias.getId(), "PAT-3"));

        var result = service.list(new ListBooksQuery(true, PageQuery.of(0, 20, "title")));

        assertThat(result.items()).extracting(item -> item.book().getTitle(), item -> item.availableCopies())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Dom Casmurro", 2L),
                        org.assertj.core.groups.Tuple.tuple("Memórias Póstumas", 1L),
                        org.assertj.core.groups.Tuple.tuple("Sem exemplares", 0L));
    }

    @Test
    void shouldNotCountBorrowedDamagedLostReservedOrInactiveCopies() {
        var book = register("9788533302273", "Dom Casmurro", 1899);
        copyRepository.save(Copy.create(book.getId(), "PAT-1"));
        copyRepository.save(Copy.reconstitute(null, book.getId(), "PAT-2", CopyStatus.BORROWED, true, 0L));
        copyRepository.save(Copy.reconstitute(null, book.getId(), "PAT-3", CopyStatus.DAMAGED, true, 0L));
        copyRepository.save(Copy.reconstitute(null, book.getId(), "PAT-4", CopyStatus.LOST, true, 0L));
        copyRepository.save(Copy.reconstitute(null, book.getId(), "PAT-5", CopyStatus.RESERVED, true, 0L));
        copyRepository.save(Copy.reconstitute(null, book.getId(), "PAT-6", CopyStatus.AVAILABLE, false, 0L));

        var found = service.findById(book.getId());

        assertThat(found).isPresent();
        assertThat(found.get().availableCopies()).isEqualTo(1L);
    }

    @Test
    void shouldShowAvailableCopiesWhenFindingById() {
        var book = register("9788533302273", "Dom Casmurro", 1899);
        copyRepository.save(Copy.create(book.getId(), "PAT-1"));
        copyRepository.save(Copy.create(book.getId(), "PAT-2"));

        assertThat(service.findById(book.getId()).orElseThrow().availableCopies()).isEqualTo(2L);
    }

    @Test
    void shouldShowAvailableCopiesWhenUpdating() {
        var book = register("9788533302273", "Dom Casmurro", 1899);
        copyRepository.save(Copy.create(book.getId(), "PAT-1"));

        var updated = service.update(new UpdateBookCommand(book.getId(), "Novo título", null, null, null, null));

        assertThat(updated.availableCopies()).isEqualTo(1L);
    }

    @Test
    void shouldShowTheTotalOfActiveCopiesNoMatterTheirStatus() {
        var book = register("9788533302273", "Dom Casmurro", 1899);
        copyRepository.save(Copy.create(book.getId(), "PAT-1"));
        copyRepository.save(Copy.reconstitute(null, book.getId(), "PAT-2", CopyStatus.BORROWED, true, 0L));
        copyRepository.save(Copy.reconstitute(null, book.getId(), "PAT-3", CopyStatus.DAMAGED, true, 0L));
        copyRepository.save(Copy.reconstitute(null, book.getId(), "PAT-4", CopyStatus.AVAILABLE, false, 0L));

        var found = service.findById(book.getId()).orElseThrow();

        assertThat(found.totalCopies()).isEqualTo(3L);
        assertThat(found.availableCopies()).isEqualTo(1L);
    }

    @Test
    void shouldShowTheTotalOfCopiesOfEachBookInTheList() {
        var domCasmurro = register("9788533302273", "Dom Casmurro", 1899);
        register("9788525406958", "Sem exemplares", 2010);
        copyRepository.save(Copy.create(domCasmurro.getId(), "PAT-1"));
        copyRepository.save(Copy.reconstitute(null, domCasmurro.getId(), "PAT-2", CopyStatus.BORROWED, true, 0L));

        var result = service.list(new ListBooksQuery(true, PageQuery.of(0, 20, "title")));

        assertThat(result.items()).extracting(item -> item.book().getTitle(), item -> item.totalCopies())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Dom Casmurro", 2L),
                        org.assertj.core.groups.Tuple.tuple("Sem exemplares", 0L));
    }

    @Test
    void shouldShowTheNamesOfTheCategoryAndOfTheAuthorsOrderedByName() {
        var secondAuthorId = authorRepository.save(Author.create("Aluísio Azevedo")).getId();
        var registered = service.register(new RegisterBookCommand("9788533302273", "Dom Casmurro", "Editora X",
                1899, categoryId, Set.of(authorId, secondAuthorId)));

        var found = service.findById(registered.book().getId()).orElseThrow();

        assertThat(found.category().getName()).isEqualTo("Ficção");
        assertThat(found.authors()).extracting(Author::getName)
                .containsExactly("Aluísio Azevedo", "Machado de Assis");
        assertThat(registered.category().getName()).isEqualTo("Ficção");
        assertThat(registered.authors()).hasSize(2);
        assertThat(registered.totalCopies()).isZero();
    }

    @Test
    void shouldShowTheNamesOfEveryBookOfThePageFromOneBatchEach() {
        var otherCategoryId = categoryRepository.save(Category.create("Poesia")).getId();
        var otherAuthorId = authorRepository.save(Author.create("Cecília Meireles")).getId();
        register("9788533302273", "Dom Casmurro", 1899);
        service.register(new RegisterBookCommand("9788535914849", "Viagem", "Editora X", 1939, otherCategoryId,
                Set.of(otherAuthorId)));

        var result = service.list(new ListBooksQuery(true, PageQuery.of(0, 20, "title")));

        assertThat(result.items()).extracting(item -> item.category().getName(),
                        item -> item.authors().get(0).getName())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Ficção", "Machado de Assis"),
                        org.assertj.core.groups.Tuple.tuple("Poesia", "Cecília Meireles"));
    }

    @Test
    void shouldKeepShowingTheNameOfAnInactiveCategoryAndAuthor() {
        var book = register("9788533302273", "Dom Casmurro", 1899);
        new CategoryService(categoryRepository).deactivate(categoryId);
        new AuthorService(authorRepository).deactivate(authorId);

        var found = service.findById(book.getId()).orElseThrow();

        assertThat(found.category().getName()).isEqualTo("Ficção");
        assertThat(found.authors()).extracting(Author::getName).containsExactly("Machado de Assis");
    }

    @Test
    void shouldShowTheNamesAndTheTotalWhenUpdating() {
        var book = register("9788533302273", "Dom Casmurro", 1899);
        copyRepository.save(Copy.create(book.getId(), "PAT-1"));

        var updated = service.update(new UpdateBookCommand(book.getId(), "Novo título", null, null, null, null));

        assertThat(updated.totalCopies()).isEqualTo(1L);
        assertThat(updated.category().getName()).isEqualTo("Ficção");
        assertThat(updated.authors()).extracting(Author::getName).containsExactly("Machado de Assis");
    }

    private Book register(String isbn, String title, Integer publicationYear) {
        return service.register(new RegisterBookCommand(isbn, title, "Editora X", publicationYear, categoryId,
                Set.of(authorId))).book();
    }

    private void deactivated(Book book) {
        book.deactivate();
        bookRepository.save(book);
    }
}
