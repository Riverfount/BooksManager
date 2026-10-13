package com.riverfount.booksmanager.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;

class AuthorTest {

    @Test
    void shouldCreateNewActiveAuthorWithGivenName() {
        var author = Author.create("Machado de Assis");

        assertThat(author.getId()).isNull();
        assertThat(author.getName()).isEqualTo("Machado de Assis");
        assertThat(author.isActive()).isTrue();
    }

    @Test
    void shouldReconstituteAuthorWithPersistedState() {
        var author = Author.reconstitute(1L, "Machado de Assis", false);

        assertThat(author.getId()).isEqualTo(1L);
        assertThat(author.getName()).isEqualTo("Machado de Assis");
        assertThat(author.isActive()).isFalse();
    }

    @Test
    void shouldNotCreateAuthorWithNullName() {
        assertThatThrownBy(() -> Author.create(null)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotCreateAuthorWithBlankName() {
        assertThatThrownBy(() -> Author.create("   ")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotCreateAuthorWithLeadingOrTrailingSpaceInName() {
        assertThatThrownBy(() -> Author.create(" Machado de Assis"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotCreateAuthorWithNameLongerThanOneHundredAndFiftyCharacters() {
        var nameWithOneHundredAndFiftyFiveCharacters = "A".repeat(155);

        assertThatThrownBy(() -> Author.create(nameWithOneHundredAndFiftyFiveCharacters))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldAcceptNameWithExactlyOneHundredAndFiftyCharacters() {
        var nameWithOneHundredAndFiftyCharacters = "A".repeat(150);

        var author = Author.create(nameWithOneHundredAndFiftyCharacters);

        assertThat(author.getName()).isEqualTo(nameWithOneHundredAndFiftyCharacters);
    }

    @Test
    void shouldRenameAuthor() {
        var author = Author.reconstitute(1L, "Machado de Assis", true);

        author.rename("Joaquim Maria Machado de Assis");

        assertThat(author.getName()).isEqualTo("Joaquim Maria Machado de Assis");
    }

    @Test
    void shouldNotRenameWithInvalidName() {
        var author = Author.reconstitute(1L, "Machado de Assis", true);

        assertThatThrownBy(() -> author.rename("")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldDeactivateAuthor() {
        var author = Author.reconstitute(1L, "Machado de Assis", true);

        author.deactivate();

        assertThat(author.isActive()).isFalse();
    }

    @Test
    void shouldKeepAuthorInactiveWhenDeactivatingTwice() {
        var author = Author.reconstitute(1L, "Machado de Assis", false);

        author.deactivate();

        assertThat(author.isActive()).isFalse();
        assertThat(author.getName()).isEqualTo("Machado de Assis");
    }
}
