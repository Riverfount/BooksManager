package com.riverfount.booksmanager.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;

class CategoryTest {

    @Test
    void shouldNotValidateUniqueNameWhenAnotherCategoryAlreadyHasTheSameName() {
        assertThatThrownBy(() -> Category.validateUniqueName("Ficção", true))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldValidateUniqueNameWhenNoOtherCategoryHasTheSameName() {
        assertThatCode(() -> Category.validateUniqueName("Ficção", false)).doesNotThrowAnyException();
    }

    @Test
    void shouldCreateNewActiveCategoryWithGivenName() {
        var category = Category.create("Ficção");

        assertThat(category.getId()).isNull();
        assertThat(category.getName()).isEqualTo("Ficção");
        assertThat(category.isActive()).isTrue();
    }

    @Test
    void shouldReconstituteCategoryWithPersistedState() {
        var category = Category.reconstitute(1L, "Ficção", false);

        assertThat(category.getId()).isEqualTo(1L);
        assertThat(category.getName()).isEqualTo("Ficção");
        assertThat(category.isActive()).isFalse();
    }

    @Test
    void shouldNotCreateCategoryWithNullName() {
        assertThatThrownBy(() -> Category.create(null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotCreateCategoryWithBlankName() {
        assertThatThrownBy(() -> Category.create("   "))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotCreateCategoryWithLeadingOrTrailingSpaceInName() {
        assertThatThrownBy(() -> Category.create(" Ficção"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotCreateCategoryWithNameLongerThanOneHundredCharacters() {
        var nameWithOneHundredAndFiveCharacters = "A".repeat(105);

        assertThatThrownBy(() -> Category.create(nameWithOneHundredAndFiveCharacters))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldAcceptNameWithExactlyOneHundredCharacters() {
        var nameWithOneHundredCharacters = "A".repeat(100);

        var category = Category.create(nameWithOneHundredCharacters);

        assertThat(category.getName()).isEqualTo(nameWithOneHundredCharacters);
    }

    @Test
    void shouldRenameCategory() {
        var category = Category.reconstitute(1L, "Ficção", true);

        category.rename("Não Ficção");

        assertThat(category.getName()).isEqualTo("Não Ficção");
    }

    @Test
    void shouldNotRenameWithInvalidName() {
        var category = Category.reconstitute(1L, "Ficção", true);

        assertThatThrownBy(() -> category.rename(""))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldDeactivateCategory() {
        var category = Category.reconstitute(1L, "Ficção", true);

        category.deactivate();

        assertThat(category.isActive()).isFalse();
    }
}
