package com.riverfount.booksmanager.catalog.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.BookWithAvailability;
import com.riverfount.booksmanager.catalog.application.port.in.DeactivateBookUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.GetBookUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.ListBooksQuery;
import com.riverfount.booksmanager.catalog.application.port.in.ListBooksUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterBookCommand;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterBookUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateBookCommand;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateBookUseCase;
import com.riverfount.booksmanager.catalog.application.port.out.AuthorRepository;
import com.riverfount.booksmanager.catalog.application.port.out.BookFilter;
import com.riverfount.booksmanager.catalog.application.port.out.BookRepository;
import com.riverfount.booksmanager.catalog.application.port.out.CategoryRepository;
import com.riverfount.booksmanager.catalog.application.port.out.CopyRepository;
import com.riverfount.booksmanager.catalog.domain.Author;
import com.riverfount.booksmanager.catalog.domain.Book;
import com.riverfount.booksmanager.catalog.domain.Isbn;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates registering, querying, searching, updating and deactivating Book: fetches what the domain
 * needs to decide (do the category and authors exist? does the ISBN
 * already exist?), calls the domain and persists. The business decision
 * itself lives in the domain (Book.create, Book.validateUniqueIsbn).
 */
@Service
@Transactional
class BookService implements RegisterBookUseCase, GetBookUseCase, ListBooksUseCase, UpdateBookUseCase,
        DeactivateBookUseCase {

    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;
    private final AuthorRepository authorRepository;
    private final CopyRepository copyRepository;
    private final Clock clock;

    BookService(BookRepository bookRepository, CategoryRepository categoryRepository,
            AuthorRepository authorRepository, CopyRepository copyRepository, Clock clock) {
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
        this.authorRepository = authorRepository;
        this.copyRepository = copyRepository;
        this.clock = clock;
    }

    @Override
    public BookWithAvailability register(RegisterBookCommand command) {
        var isbn = new Isbn(command.isbn());
        // built before the cross-checks: this way, categoryId and
        // authorIds are already guaranteed non-null when we query the
        // other repositories.
        var book = Book.create(isbn, command.title(), command.publisher(), command.publicationYear(),
                command.categoryId(), command.authorIds(), clock);
        validateCategoryExists(book.getCategoryId());
        validateAuthorsExist(book.getAuthorIds());
        Book.validateUniqueIsbn(isbn, bookRepository.existsByIsbn(isbn));
        // a new book has no copies yet
        return new BookWithAvailability(bookRepository.save(book), 0);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BookWithAvailability> findById(Long id) {
        return bookRepository.findById(id).map(this::withAvailability);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<BookWithAvailability> list(ListBooksQuery query) {
        var filter = new BookFilter(query.title(), query.author(), query.isbn(), query.categoryId());
        var page = bookRepository.findAll(filter, query.active(), query.page());
        // one aggregated query for the whole page, not one per book
        var bookIds = page.items().stream().map(Book::getId).collect(Collectors.toSet());
        var availableCopies = copyRepository.countAvailableByBookIds(bookIds);
        return page.map(book -> new BookWithAvailability(book, availableCopies.getOrDefault(book.getId(), 0L)));
    }

    @Override
    public BookWithAvailability update(UpdateBookCommand command) {
        var book = bookRepository.findById(command.id())
                .orElseThrow(() -> new ResourceNotFoundException("livro não encontrado: " + command.id()));
        var previousCategoryId = book.getCategoryId();
        // a null field keeps the current value; the ISBN is never touched (RN01)
        book.updateDetails(
                command.title() != null ? command.title() : book.getTitle(),
                command.publisher() != null ? command.publisher() : book.getPublisher(),
                command.publicationYear() != null ? command.publicationYear() : book.getPublicationYear(),
                command.categoryId() != null ? command.categoryId() : book.getCategoryId(),
                clock);
        // only a category or authors the book does not have yet need to be
        // active: one inactivated after being linked stays accepted (HU18, HU21)
        if (command.categoryId() != null && !Objects.equals(command.categoryId(), previousCategoryId)) {
            validateCategoryExists(command.categoryId());
        }
        if (command.authorIds() != null) {
            var currentAuthorIds = book.getAuthorIds();
            book.replaceAuthors(command.authorIds());
            var addedAuthorIds = book.getAuthorIds().stream()
                    .filter(authorId -> !currentAuthorIds.contains(authorId))
                    .collect(Collectors.toSet());
            if (!addedAuthorIds.isEmpty()) {
                validateAuthorsExist(addedAuthorIds);
            }
        }
        return withAvailability(bookRepository.save(book));
    }

    @Override
    public void deactivate(Long id) {
        var book = bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("livro não encontrado: " + id));
        book.deactivate();
        bookRepository.save(book);
    }

    private BookWithAvailability withAvailability(Book book) {
        var availableCopies = copyRepository.countAvailableByBookIds(Set.of(book.getId()));
        return new BookWithAvailability(book, availableCopies.getOrDefault(book.getId(), 0L));
    }

    private void validateCategoryExists(Long categoryId) {
        var category = categoryRepository.findById(categoryId);
        if (category.isEmpty()) {
            throw new BusinessRuleException("categoria não encontrada: " + categoryId);
        }
        if (!category.get().isActive()) {
            throw new BusinessRuleException("categoria inativa: " + categoryId);
        }
    }

    private void validateAuthorsExist(Set<Long> authorIds) {
        // a single batch query, instead of one per authorId
        var found = authorRepository.findAllByIds(authorIds);
        var foundIds = found.stream().map(Author::getId).collect(Collectors.toSet());
        for (var authorId : authorIds) {
            if (!foundIds.contains(authorId)) {
                throw new BusinessRuleException("autor não encontrado: " + authorId);
            }
        }
        for (var author : found) {
            if (!author.isActive()) {
                throw new BusinessRuleException("autor inativo: " + author.getId());
            }
        }
    }
}
