package com.riverfount.booksmanager.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.catalog.application.port.in.ListCopiesQuery;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCopyCommand;
import com.riverfount.booksmanager.catalog.application.port.in.UpdateCopyCommand;
import com.riverfount.booksmanager.catalog.domain.Book;
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

class CopyServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);

    private CopyService service;
    private InMemoryCopyRepository copyRepository;
    private InMemoryBookRepository bookRepository;
    private Long bookId;

    @BeforeEach
    void setUp() {
        copyRepository = new InMemoryCopyRepository();
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

    @Test
    void shouldListOnlyActiveCopiesByDefault() {
        service.register(new RegisterCopyCommand(bookId, "PAT-1"));
        var inactive = service.register(new RegisterCopyCommand(bookId, "PAT-2"));
        deactivate(inactive);

        var result = service.list(new ListCopiesQuery(null, null, true, PageQuery.of(0, 20, "assetCode")));

        assertThat(result.items()).extracting(Copy::getAssetCode).containsExactly("PAT-1");
    }

    @Test
    void shouldListInactiveCopiesWhenRequested() {
        service.register(new RegisterCopyCommand(bookId, "PAT-1"));
        var inactive = service.register(new RegisterCopyCommand(bookId, "PAT-2"));
        deactivate(inactive);

        var result = service.list(new ListCopiesQuery(null, null, false, PageQuery.of(0, 20, "assetCode")));

        assertThat(result.items()).extracting(Copy::getAssetCode).containsExactly("PAT-2");
    }

    @Test
    void shouldFilterByBook() {
        var otherBookId = bookRepository.save(Book.create(new Isbn("080442957X"), "Outro Livro", "Editora Y",
                2000, 1L, Set.of(1L), FIXED_CLOCK)).getId();
        service.register(new RegisterCopyCommand(bookId, "PAT-1"));
        service.register(new RegisterCopyCommand(otherBookId, "PAT-2"));

        var result = service.list(new ListCopiesQuery(otherBookId, null, true, PageQuery.of(0, 20, "assetCode")));

        assertThat(result.items()).extracting(Copy::getAssetCode).containsExactly("PAT-2");
    }

    @Test
    void shouldFilterByStatus() {
        service.register(new RegisterCopyCommand(bookId, "PAT-1"));
        var borrowed = service.register(new RegisterCopyCommand(bookId, "PAT-2"));
        borrowed.checkOut();
        copyRepository.save(borrowed);

        var result = service.list(new ListCopiesQuery(null, CopyStatus.BORROWED, true,
                PageQuery.of(0, 20, "assetCode")));

        assertThat(result.items()).extracting(Copy::getAssetCode).containsExactly("PAT-2");
    }

    @Test
    void shouldPaginateAndSortCopies() {
        service.register(new RegisterCopyCommand(bookId, "PAT-3"));
        service.register(new RegisterCopyCommand(bookId, "PAT-1"));
        service.register(new RegisterCopyCommand(bookId, "PAT-2"));

        var firstPage = service.list(new ListCopiesQuery(null, null, true, PageQuery.of(0, 2, "assetCode")));
        var secondPage = service.list(new ListCopiesQuery(null, null, true, PageQuery.of(1, 2, "assetCode")));

        assertThat(firstPage.items()).extracting(Copy::getAssetCode).containsExactly("PAT-1", "PAT-2");
        assertThat(secondPage.items()).extracting(Copy::getAssetCode).containsExactly("PAT-3");
        assertThat(firstPage.totalItems()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);
    }

    @Test
    void shouldRejectUnknownSortField() {
        assertThatThrownBy(() -> new ListCopiesQuery(null, null, true, PageQuery.of(0, 20, "bookId")))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessageContaining("bookId");
    }

    @Test
    void shouldMarkCopyAsDamagedKeepingTheOtherFields() {
        var copy = service.register(new RegisterCopyCommand(bookId, "PAT-1"));

        var updated = service.update(new UpdateCopyCommand(copy.getId(), CopyStatus.DAMAGED));

        assertThat(updated.getStatus()).isEqualTo(CopyStatus.DAMAGED);
        assertThat(updated.getAssetCode()).isEqualTo("PAT-1");
        assertThat(updated.getBookId()).isEqualTo(bookId);
        assertThat(copyRepository.findById(copy.getId()).orElseThrow().getStatus())
                .isEqualTo(CopyStatus.DAMAGED);
    }

    @Test
    void shouldKeepEverythingWhenNoFieldIsSent() {
        var copy = service.register(new RegisterCopyCommand(bookId, "PAT-1"));

        var updated = service.update(new UpdateCopyCommand(copy.getId(), null));

        assertThat(updated.getStatus()).isEqualTo(CopyStatus.AVAILABLE);
        assertThat(updated.isActive()).isTrue();
    }

    @Test
    void shouldNotUpdateNonExistentCopy() {
        assertThatThrownBy(() -> service.update(new UpdateCopyCommand(99L, CopyStatus.LOST)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void shouldNotManuallySetBorrowed() {
        var copy = service.register(new RegisterCopyCommand(bookId, "PAT-1"));

        assertThatThrownBy(() -> service.update(new UpdateCopyCommand(copy.getId(), CopyStatus.BORROWED)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(copyRepository.findById(copy.getId()).orElseThrow().getStatus())
                .isEqualTo(CopyStatus.AVAILABLE);
    }

    @Test
    void shouldNotMarkBorrowedCopyAsLost() {
        var copy = service.register(new RegisterCopyCommand(bookId, "PAT-1"));
        copy.checkOut();
        copyRepository.save(copy);

        assertThatThrownBy(() -> service.update(new UpdateCopyCommand(copy.getId(), CopyStatus.LOST)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(copyRepository.findById(copy.getId()).orElseThrow().getStatus())
                .isEqualTo(CopyStatus.BORROWED);
    }

    @Test
    void shouldDeactivateExistingCopyKeepingItStored() {
        var copy = service.register(new RegisterCopyCommand(bookId, "PAT-1"));

        service.deactivate(copy.getId());

        var stored = copyRepository.findById(copy.getId()).orElseThrow();
        assertThat(stored.isActive()).isFalse();
        assertThat(stored.getAssetCode()).isEqualTo("PAT-1");
        assertThat(stored.getBookId()).isEqualTo(bookId);
    }

    @Test
    void shouldBeIdempotentWhenDeactivatingAnInactiveCopy() {
        var copy = service.register(new RegisterCopyCommand(bookId, "PAT-1"));
        deactivate(copy);

        service.deactivate(copy.getId());

        assertThat(copyRepository.findById(copy.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void shouldNotDeactivateNonExistentCopy() {
        assertThatThrownBy(() -> service.deactivate(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void shouldNotDeactivateBorrowedCopy() {
        var copy = service.register(new RegisterCopyCommand(bookId, "PAT-1"));
        copy.checkOut();
        copyRepository.save(copy);

        assertThatThrownBy(() -> service.deactivate(copy.getId()))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(copyRepository.findById(copy.getId()).orElseThrow().isActive()).isTrue();
    }

    private void deactivate(Copy copy) {
        copy.deactivate();
        copyRepository.save(copy);
    }
}
