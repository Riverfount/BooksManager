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

    @Test
    void shouldAcceptNullPublicationYear() {
        var book = Book.create(ISBN, "Dom Casmurro", "Editora X", null, 1L, Set.of(10L), FIXED_CLOCK);

        assertThat(book.getPublicationYear()).isNull();
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
}
