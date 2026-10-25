package com.riverfount.booksmanager.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.audit.application.RecordedAudit;
import com.riverfount.booksmanager.audit.application.port.in.AuditEvent;
import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterAuthorCommand;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterBookCommand;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCategoryCommand;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCopyCommand;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What deactivating and reactivating a book, an author, a category and a copy report to the audit trail (RF27,
 * RN14): one event when the state really changed, none when it did not (a second deactivation, a reactivation of
 * what is active), none when the operation is refused, and none for registering or editing. The summary is the title,
 * the name or the asset code, and never more.
 */
class CatalogServicesAuditTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);

    private RecordedAudit audit;
    private InMemoryAuthorRepository authorRepository;
    private InMemoryCategoryRepository categoryRepository;
    private InMemoryBookRepository bookRepository;
    private InMemoryCopyRepository copyRepository;
    private AuthorService authors;
    private CategoryService categories;
    private BookService books;
    private CopyService copies;
    private StubActiveLoanChecker activeLoans;
    private Long authorId;
    private Long categoryId;
    private Long bookId;

    @BeforeEach
    void setUp() {
        audit = new RecordedAudit();
        authorRepository = new InMemoryAuthorRepository();
        categoryRepository = new InMemoryCategoryRepository();
        bookRepository = new InMemoryBookRepository(authorRepository);
        copyRepository = new InMemoryCopyRepository();
        authors = new AuthorService(authorRepository, audit);
        categories = new CategoryService(categoryRepository, audit);
        books = new BookService(bookRepository, categoryRepository, authorRepository, copyRepository, CLOCK, audit);
        activeLoans = new StubActiveLoanChecker();
        copies = new CopyService(copyRepository, bookRepository, activeLoans, audit);

        authorId = authors.register(new RegisterAuthorCommand("Machado de Assis")).getId();
        categoryId = categories.register(new RegisterCategoryCommand("Romance")).getId();
        bookId = books.register(new RegisterBookCommand("9788533302273", "Dom Casmurro", "Garnier", 1899, categoryId,
                Set.of(authorId))).book().getId();
    }

    private AuditEvent only() {
        return audit.only();
    }

    // registering and editing are not audited

    @Test
    void registeringAndEditingTheCatalogReportsNothing() {
        var copyId = copies.register(new RegisterCopyCommand(bookId, "PAT-1")).getId();
        authors.update(new com.riverfount.booksmanager.catalog.application.port.in.UpdateAuthorCommand(authorId,
                "Machado"));
        categories.update(new com.riverfount.booksmanager.catalog.application.port.in.UpdateCategoryCommand(
                categoryId, "Ficção"));
        books.update(new com.riverfount.booksmanager.catalog.application.port.in.UpdateBookCommand(bookId, "Novo",
                null, null, null, null));
        copies.update(new com.riverfount.booksmanager.catalog.application.port.in.UpdateCopyCommand(copyId,
                com.riverfount.booksmanager.catalog.domain.CopyStatus.DAMAGED));

        assertThat(audit.all()).isEmpty();
    }

    // book

    @Test
    void deactivatingABookReportsItsTitle() {
        books.deactivate(bookId);

        assertThat(only()).isEqualTo(new AuditEvent(AuditAction.BOOK_DEACTIVATED, AuditTarget.BOOK, bookId,
                "Dom Casmurro"));
    }

    @Test
    void deactivatingABookThatIsAlreadyInactiveReportsNothingTheSecondTime() {
        books.deactivate(bookId);
        books.deactivate(bookId);

        assertThat(audit.actions()).containsExactly(AuditAction.BOOK_DEACTIVATED);
    }

    @Test
    void reactivatingABookReportsItsTitle() {
        books.deactivate(bookId);
        books.reactivate(bookId);

        assertThat(audit.events()).containsExactly(
                new AuditEvent(AuditAction.BOOK_DEACTIVATED, AuditTarget.BOOK, bookId, "Dom Casmurro"),
                new AuditEvent(AuditAction.BOOK_REACTIVATED, AuditTarget.BOOK, bookId, "Dom Casmurro"));
    }

    @Test
    void reactivatingAnActiveBookReportsNothing() {
        books.reactivate(bookId);

        assertThat(audit.all()).isEmpty();
    }

    @Test
    void aRefusedReactivationOfABookReportsNothing() {
        books.deactivate(bookId);
        categories.deactivate(categoryId);
        var before = audit.all().size();

        assertThatThrownBy(() -> books.reactivate(bookId)).isInstanceOf(BusinessRuleException.class);

        assertThat(audit.all()).hasSize(before);
    }

    @Test
    void deactivatingABookThatDoesNotExistReportsNothing() {
        assertThatThrownBy(() -> books.deactivate(999L)).isInstanceOf(ResourceNotFoundException.class);

        assertThat(audit.all()).isEmpty();
    }

    // author

    @Test
    void deactivatingAndReactivatingAnAuthorReportTheName() {
        authors.deactivate(authorId);
        authors.reactivate(authorId, false);

        assertThat(audit.events()).containsExactly(
                new AuditEvent(AuditAction.AUTHOR_DEACTIVATED, AuditTarget.AUTHOR, authorId, "Machado de Assis"),
                new AuditEvent(AuditAction.AUTHOR_REACTIVATED, AuditTarget.AUTHOR, authorId, "Machado de Assis"));
    }

    @Test
    void anAuthorDeactivatedTwiceOrReactivatedWhenActiveReportsOnce() {
        authors.reactivate(authorId, false);
        authors.deactivate(authorId);
        authors.deactivate(authorId);

        assertThat(audit.actions()).containsExactly(AuditAction.AUTHOR_DEACTIVATED);
    }

    @Test
    void aRefusedReactivationOfAnAuthorForARepeatedNameReportsNothing() {
        authors.deactivate(authorId);
        authors.register(new RegisterAuthorCommand("Machado de Assis"));
        var before = audit.all().size();

        assertThatThrownBy(() -> authors.reactivate(authorId, false)).isInstanceOf(BusinessRuleException.class);

        assertThat(audit.all()).hasSize(before);
    }

    // category

    @Test
    void deactivatingAndReactivatingACategoryReportTheName() {
        categories.deactivate(categoryId);
        categories.reactivate(categoryId);

        assertThat(audit.events()).containsExactly(
                new AuditEvent(AuditAction.CATEGORY_DEACTIVATED, AuditTarget.CATEGORY, categoryId, "Romance"),
                new AuditEvent(AuditAction.CATEGORY_REACTIVATED, AuditTarget.CATEGORY, categoryId, "Romance"));
    }

    @Test
    void aCategoryDeactivatedTwiceOrReactivatedWhenActiveReportsOnce() {
        categories.reactivate(categoryId);
        categories.deactivate(categoryId);
        categories.deactivate(categoryId);

        assertThat(audit.actions()).containsExactly(AuditAction.CATEGORY_DEACTIVATED);
    }

    // copy

    @Test
    void deactivatingAndReactivatingACopyReportTheAssetCode() {
        var copyId = copies.register(new RegisterCopyCommand(bookId, "PAT-1")).getId();

        copies.deactivate(copyId);
        copies.reactivate(copyId);

        assertThat(audit.events()).containsExactly(
                new AuditEvent(AuditAction.COPY_DEACTIVATED, AuditTarget.COPY, copyId, "PAT-1"),
                new AuditEvent(AuditAction.COPY_REACTIVATED, AuditTarget.COPY, copyId, "PAT-1"));
    }

    @Test
    void aCopyDeactivatedTwiceOrReactivatedWhenActiveReportsOnce() {
        var copyId = copies.register(new RegisterCopyCommand(bookId, "PAT-1")).getId();

        copies.reactivate(copyId);
        copies.deactivate(copyId);
        copies.deactivate(copyId);

        assertThat(audit.actions()).containsExactly(AuditAction.COPY_DEACTIVATED);
    }

    @Test
    void aRefusedDeactivationOfACopyOnLoanReportsNothing() {
        var copyId = copies.register(new RegisterCopyCommand(bookId, "PAT-1")).getId();
        activeLoans.markActiveLoan(copyId);

        assertThatThrownBy(() -> copies.deactivate(copyId)).isInstanceOf(BusinessRuleException.class);

        assertThat(audit.all()).isEmpty();
    }

    @Test
    void aRefusedReactivationOfACopyOfAnInactiveBookReportsNothing() {
        var copyId = copies.register(new RegisterCopyCommand(bookId, "PAT-1")).getId();
        copies.deactivate(copyId);
        books.deactivate(bookId);
        var before = audit.all().size();

        assertThatThrownBy(() -> copies.reactivate(copyId)).isInstanceOf(BusinessRuleException.class);

        assertThat(audit.all()).hasSize(before);
    }
}
