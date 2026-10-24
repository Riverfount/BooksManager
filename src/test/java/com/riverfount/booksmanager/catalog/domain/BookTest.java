package com.riverfount.booksmanager.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BookTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);
    private static final Isbn ISBN = new Isbn("9788533302273");

    @Test
    void shouldCreateNewActiveBookWithGivenData() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L, 20L), FIXED_CLOCK);

        assertThat(book.getId()).isNull();
        assertThat(book.getIsbn()).isEqualTo(ISBN);
        assertThat(book.getTitle()).isEqualTo("Dom Casmurro");
        assertThat(book.getPublisher()).isEqualTo("Editora X");
        assertThat(book.getPublicationYear()).isEqualTo(1899);
        assertThat(book.getCategoryId()).isEqualTo(1L);
        assertThat(book.getAuthorIds()).containsExactlyInAnyOrder(10L, 20L);
        assertThat(book.isActive()).isTrue();
    }

    @Test
    void shouldReconstituteBookWithPersistedState() {
        var book = Book.reconstitute(1L, ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), false);

        assertThat(book.getId()).isEqualTo(1L);
        assertThat(book.isActive()).isFalse();
    }

    @Test
    void shouldNotReconstituteWithNullAuthorIds() {
        // a null authorIds here can only come from a bug in the
        // persistence adapter, not from user input — hence
        // NullPointerException, not BusinessRuleException
        assertThatThrownBy(() -> Book.reconstitute(1L, ISBN, "Dom Casmurro", "Editora X", 1899, 1L, null, true))
                .isInstanceOf(NullPointerException.class);
    }

    // every book has a publisher and a publication year (RF01); a book that predates the rule keeps working

    @Test
    void shouldNotCreateABookWithoutPublicationYear() {
        assertThatThrownBy(() -> Book.create(ISBN, "Dom Casmurro", "Editora X", null, 1L, Set.of(10L), FIXED_CLOCK))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ano de publicação é obrigatório");
    }

    @Test
    void shouldNotCreateABookWithoutPublisher() {
        assertThatThrownBy(() -> Book.create(ISBN, "Dom Casmurro", null, 1899, 1L, Set.of(10L), FIXED_CLOCK))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("editora do livro é obrigatória");
    }

    @Test
    void shouldNotCreateABookWithABlankPublisher() {
        assertThatThrownBy(() -> Book.create(ISBN, "Dom Casmurro", "", 1899, 1L, Set.of(10L), FIXED_CLOCK))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Book.create(ISBN, "Dom Casmurro", "   ", 1899, 1L, Set.of(10L), FIXED_CLOCK))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotUpdateTheBookToABlankPublisher() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), FIXED_CLOCK);

        assertThatThrownBy(() -> book.updateDetails("Dom Casmurro", "", 1899, 1L, FIXED_CLOCK))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("editora");
        assertThatThrownBy(() -> book.updateDetails("Dom Casmurro", "  ", 1899, 1L, FIXED_CLOCK))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(book.getPublisher()).isEqualTo("Editora X");
    }

    @Test
    void aBookThatPredatesTheRuleWithoutPublisherAndYearShouldStayEditableKeepingThem() {
        var legacy = Book.reconstitute(1L, ISBN, "Dom Casmurro", null, null, 1L, Set.of(10L), true);

        legacy.updateDetails("Dom Casmurro (revisado)", null, null, 1L, FIXED_CLOCK);

        assertThat(legacy.getTitle()).isEqualTo("Dom Casmurro (revisado)");
        assertThat(legacy.getPublisher()).isNull();
        assertThat(legacy.getPublicationYear()).isNull();
    }

    @Test
    void aBookThatPredatesTheRuleCanBeCompletedByTheUpdate() {
        var legacy = Book.reconstitute(1L, ISBN, "Dom Casmurro", null, null, 1L, Set.of(10L), true);

        legacy.updateDetails("Dom Casmurro", "Garnier", 1899, 1L, FIXED_CLOCK);

        assertThat(legacy.getPublisher()).isEqualTo("Garnier");
        assertThat(legacy.getPublicationYear()).isEqualTo(1899);
    }

    @Test
    void shouldAcceptPublicationYearEqualToCurrentYear() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", 2026, 1L, Set.of(10L), FIXED_CLOCK);

        assertThat(book.getPublicationYear()).isEqualTo(2026);
    }

    @Test
    void shouldNotAcceptFuturePublicationYear() {
        assertThatThrownBy(() -> Book.create(ISBN, "Dom Casmurro", "Editora X", 2027, 1L, Set.of(10L), FIXED_CLOCK))
                .isInstanceOf(BusinessRuleException.class);
    }

    // the publisher (the column has 255 characters) and the year (RF01)

    @Test
    void shouldAcceptPublisherWithTheMaximumLength() {
        var publisher = "e".repeat(Book.MAX_PUBLISHER_LENGTH);

        var book = Book.create(ISBN, "Dom Casmurro", publisher, 1899, 1L, Set.of(10L), FIXED_CLOCK);

        assertThat(book.getPublisher()).isEqualTo(publisher);
    }

    @Test
    void shouldNotAcceptPublisherLongerThanTheMaximum() {
        var publisher = "e".repeat(Book.MAX_PUBLISHER_LENGTH + 1);

        assertThatThrownBy(() -> Book.create(ISBN, "Dom Casmurro", publisher, 1899, 1L, Set.of(10L), FIXED_CLOCK))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("editora")
                .hasMessageContaining("255");
    }

    @Test
    void shouldNotUpdateWithPublisherLongerThanTheMaximum() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), FIXED_CLOCK);
        var publisher = "e".repeat(Book.MAX_PUBLISHER_LENGTH + 1);

        assertThatThrownBy(() -> book.updateDetails("Dom Casmurro", publisher, 1899, 1L, FIXED_CLOCK))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldAcceptPublicationYearAtTheFloor() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", Book.MIN_PUBLICATION_YEAR, 1L, Set.of(10L),
                FIXED_CLOCK);

        assertThat(book.getPublicationYear()).isEqualTo(1450);
    }

    @Test
    void shouldNotAcceptPublicationYearBelowTheFloor() {
        assertThatThrownBy(() -> Book.create(ISBN, "Dom Casmurro", "Editora X", 1449, 1L, Set.of(10L), FIXED_CLOCK))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("1450");
    }

    @Test
    void shouldNotAcceptZeroOrNegativePublicationYear() {
        assertThatThrownBy(() -> Book.create(ISBN, "Dom Casmurro", "Editora X", 0, 1L, Set.of(10L), FIXED_CLOCK))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Book.create(ISBN, "Dom Casmurro", "Editora X", -5, 1L, Set.of(10L), FIXED_CLOCK))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotUpdateWithPublicationYearBelowTheFloor() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), FIXED_CLOCK);

        assertThatThrownBy(() -> book.updateDetails("Dom Casmurro", "Editora X", 12, 1L, FIXED_CLOCK))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotCreateBookWithNullIsbn() {
        assertThatThrownBy(() -> Book.create(null, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), FIXED_CLOCK))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotCreateBookWithBlankTitle() {
        assertThatThrownBy(() -> Book.create(ISBN, "  ", "Editora X", 1899, 1L, Set.of(10L), FIXED_CLOCK))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotCreateBookWithLeadingOrTrailingSpaceInTitle() {
        assertThatThrownBy(
                () -> Book.create(ISBN, " Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), FIXED_CLOCK))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotCreateBookWithTitleLongerThanTwoHundredAndFiftyFiveCharacters() {
        var titleWithTwoHundredAndFiftySixCharacters = "A".repeat(256);

        assertThatThrownBy(() -> Book.create(ISBN, titleWithTwoHundredAndFiftySixCharacters, "Editora X", 1899, 1L,
                Set.of(10L), FIXED_CLOCK))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldAcceptTitleWithExactlyTwoHundredAndFiftyFiveCharacters() {
        var titleWithTwoHundredAndFiftyFiveCharacters = "A".repeat(255);

        var book = Book.create(ISBN, titleWithTwoHundredAndFiftyFiveCharacters, "Editora X", 1899, 1L,
                Set.of(10L), FIXED_CLOCK);

        assertThat(book.getTitle()).isEqualTo(titleWithTwoHundredAndFiftyFiveCharacters);
    }

    @Test
    void shouldNotCreateBookWithNullCategory() {
        assertThatThrownBy(
                () -> Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, null, Set.of(10L), FIXED_CLOCK))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotCreateBookWithoutAuthor() {
        assertThatThrownBy(() -> Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(), FIXED_CLOCK))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotCreateBookWithNullAuthorIds() {
        assertThatThrownBy(() -> Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, null, FIXED_CLOCK))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldUpdateBookDetails() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), FIXED_CLOCK);

        book.updateDetails("Dom Casmurro (revisado)", "Editora Y", 1900, 2L, FIXED_CLOCK);

        assertThat(book.getTitle()).isEqualTo("Dom Casmurro (revisado)");
        assertThat(book.getPublisher()).isEqualTo("Editora Y");
        assertThat(book.getPublicationYear()).isEqualTo(1900);
        assertThat(book.getCategoryId()).isEqualTo(2L);
    }

    @Test
    void shouldNotUpdateWithInvalidTitle() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), FIXED_CLOCK);

        assertThatThrownBy(() -> book.updateDetails("", "Editora X", 1899, 1L, FIXED_CLOCK))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotUpdateWithFuturePublicationYear() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), FIXED_CLOCK);

        assertThatThrownBy(() -> book.updateDetails("Dom Casmurro", "Editora X", 2027, 1L, FIXED_CLOCK))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotUpdateWithNullCategory() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), FIXED_CLOCK);

        assertThatThrownBy(() -> book.updateDetails("Dom Casmurro", "Editora X", 1899, null, FIXED_CLOCK))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldAddAuthor() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), FIXED_CLOCK);

        book.addAuthor(20L);

        assertThat(book.getAuthorIds()).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    void shouldNotAddNullAuthor() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), FIXED_CLOCK);

        assertThatThrownBy(() -> book.addAuthor(null)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotRemoveNullAuthor() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L, 20L), FIXED_CLOCK);

        assertThatThrownBy(() -> book.removeAuthor(null)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void removingAuthorNotInTheBookShouldBeNoOp() {
        // same semantics as Set.remove: removing something that is
        // already absent is not an error, the final state is already
        // the desired one
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L, 20L), FIXED_CLOCK);

        book.removeAuthor(999L);

        assertThat(book.getAuthorIds()).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    void shouldRemoveAuthorWhenThereIsMoreThanOne() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L, 20L), FIXED_CLOCK);

        book.removeAuthor(10L);

        assertThat(book.getAuthorIds()).containsExactly(20L);
    }

    @Test
    void shouldNotRemoveTheLastAuthor() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), FIXED_CLOCK);

        assertThatThrownBy(() -> book.removeAuthor(10L)).isInstanceOf(BusinessRuleException.class);
        assertThat(book.getAuthorIds()).containsExactly(10L);
    }

    @Test
    void shouldReplaceAuthors() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L, 20L), FIXED_CLOCK);

        book.replaceAuthors(Set.of(20L, 30L));

        assertThat(book.getAuthorIds()).containsExactlyInAnyOrder(20L, 30L);
    }

    @Test
    void shouldNotReplaceAuthorsWithEmptySet() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), FIXED_CLOCK);

        assertThatThrownBy(() -> book.replaceAuthors(Set.of())).isInstanceOf(BusinessRuleException.class);
        assertThat(book.getAuthorIds()).containsExactly(10L);
    }

    @Test
    void shouldNotReplaceAuthorsWithNullSet() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), FIXED_CLOCK);

        assertThatThrownBy(() -> book.replaceAuthors(null)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotReplaceAuthorsWithNullElement() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), FIXED_CLOCK);
        var withNull = new java.util.HashSet<Long>();
        withNull.add(null);

        assertThatThrownBy(() -> book.replaceAuthors(withNull)).isInstanceOf(BusinessRuleException.class);
        assertThat(book.getAuthorIds()).containsExactly(10L);
    }

    @Test
    void deactivatingAnInactiveBookShouldBeNoOp() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), FIXED_CLOCK);
        book.deactivate();

        book.deactivate();

        assertThat(book.isActive()).isFalse();
    }

    @Test
    void deactivatingShouldKeepTheBookData() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), FIXED_CLOCK);

        book.deactivate();

        assertThat(book.getIsbn()).isEqualTo(ISBN);
        assertThat(book.getTitle()).isEqualTo("Dom Casmurro");
        assertThat(book.getCategoryId()).isEqualTo(1L);
        assertThat(book.getAuthorIds()).containsExactly(10L);
    }

    @Test
    void shouldDeactivateBook() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), FIXED_CLOCK);

        book.deactivate();

        assertThat(book.isActive()).isFalse();
    }

    @Test
    void exposedAuthorIdsShouldBeAnImmutableCopy() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 1L, Set.of(10L), FIXED_CLOCK);
        var authorIds = book.getAuthorIds();

        assertThatThrownBy(() -> authorIds.add(99L)).isInstanceOf(UnsupportedOperationException.class);

        book.addAuthor(30L);
        assertThat(authorIds).doesNotContain(30L);
    }

    // reactivating a book (RN14): its category and its authors have to be active

    private static Book inactiveBook() {
        return Book.reconstitute(1L, ISBN, "Dom Casmurro", "Editora X", 1899, 5L, Set.of(10L, 20L), false);
    }

    @Test
    void shouldReactivateAnInactiveBookWhoseCategoryAndAuthorsAreActive() {
        var book = inactiveBook();

        book.reactivate(Category.reconstitute(5L, "Romance", true),
                java.util.List.of(Author.reconstitute(10L, "Machado de Assis", true),
                        Author.reconstitute(20L, "Outro Autor", true)));

        assertThat(book.isActive()).isTrue();
        assertThat(book.getTitle()).isEqualTo("Dom Casmurro");
        assertThat(book.getAuthorIds()).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    void shouldNotReactivateABookWhoseCategoryIsInactiveNamingIt() {
        var book = inactiveBook();

        assertThatThrownBy(() -> book.reactivate(Category.reconstitute(5L, "Romance", false),
                java.util.List.of(Author.reconstitute(10L, "Machado de Assis", true))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("categoria inativa: Romance");

        assertThat(book.isActive()).isFalse();
    }

    @Test
    void shouldNotReactivateABookWithAnInactiveAuthorNamingEveryOne() {
        var book = inactiveBook();

        assertThatThrownBy(() -> book.reactivate(Category.reconstitute(5L, "Romance", true),
                java.util.List.of(Author.reconstitute(10L, "Machado de Assis", false),
                        Author.reconstitute(20L, "Outro Autor", false))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("autor inativo: Machado de Assis")
                .hasMessageContaining("autor inativo: Outro Autor");

        assertThat(book.isActive()).isFalse();
    }

    @Test
    void shouldReportTheInactiveCategoryAndAuthorsTogether() {
        var book = inactiveBook();

        assertThatThrownBy(() -> book.reactivate(Category.reconstitute(5L, "Romance", false),
                java.util.List.of(Author.reconstitute(10L, "Machado de Assis", false),
                        Author.reconstitute(20L, "Outro Autor", true))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("categoria inativa: Romance")
                .hasMessageContaining("autor inativo: Machado de Assis")
                .hasMessageNotContaining("Outro Autor");
    }

    @Test
    void reactivatingAnActiveBookShouldChangeNothingEvenIfItsCategoryIsInactive() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", 1899, 5L, Set.of(10L), FIXED_CLOCK);

        book.reactivate(Category.reconstitute(5L, "Romance", false),
                java.util.List.of(Author.reconstitute(10L, "Machado de Assis", false)));

        assertThat(book.isActive()).isTrue();
    }
}
