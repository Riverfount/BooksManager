package com.riverfount.booksmanager.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.catalog.application.port.in.CategoryCopies;
import com.riverfount.booksmanager.catalog.application.port.out.CatalogSummaryRepository;
import com.riverfount.booksmanager.catalog.application.port.out.CategoryCopyCount;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The catalog's summary for the dashboard (RF25): the active copies, in all and by category. The counting
 * itself is the repository's (and is tested against PostgreSQL); here is what the service decides with it.
 */
class CatalogSummaryServiceTest {

    private static CatalogSummaryService serviceOver(CategoryCopyCount... rows) {
        CatalogSummaryRepository repository = () -> List.of(rows);
        return new CatalogSummaryService(repository);
    }

    @Test
    void shouldSumTheCopiesOfEveryCategoryIntoTheTotal() {
        var summary = serviceOver(
                new CategoryCopyCount(1L, "Ficção", true, 3, 7),
                new CategoryCopyCount(2L, "Poesia", true, 1, 2)).get();

        assertThat(summary.totalCopies()).isEqualTo(9L);
        assertThat(summary.categories()).containsExactly(
                new CategoryCopies(1L, "Ficção", 3, 7), new CategoryCopies(2L, "Poesia", 1, 2));
    }

    @Test
    void anActiveCategoryWithoutBooksShouldAppearWithZero() {
        var summary = serviceOver(new CategoryCopyCount(1L, "Ficção", true, 0, 0)).get();

        assertThat(summary.categories()).containsExactly(new CategoryCopies(1L, "Ficção", 0, 0));
        assertThat(summary.totalCopies()).isZero();
    }

    @Test
    void anInactiveCategoryShouldAppearOnlyWhileItStillHoldsBooksOrCopies() {
        var summary = serviceOver(
                new CategoryCopyCount(1L, "Antiga vazia", false, 0, 0),
                new CategoryCopyCount(2L, "Antiga com livros", false, 2, 0),
                new CategoryCopyCount(3L, "Antiga com exemplares", false, 0, 4)).get();

        assertThat(summary.categories()).extracting(CategoryCopies::categoryName)
                .containsExactly("Antiga com livros", "Antiga com exemplares");
    }

    @Test
    void theTotalShouldKeepTheCopiesOfAnInactiveCategoryToo() {
        var summary = serviceOver(
                new CategoryCopyCount(1L, "Ficção", true, 1, 5),
                new CategoryCopyCount(2L, "Antiga", false, 1, 3)).get();

        assertThat(summary.totalCopies()).isEqualTo(8L);
        assertThat(summary.categories().stream().mapToLong(CategoryCopies::copies).sum())
                .isEqualTo(summary.totalCopies());
    }

    @Test
    void shouldHaveNothingToSumWhenThereAreNoCategories() {
        var summary = serviceOver().get();

        assertThat(summary.totalCopies()).isZero();
        assertThat(summary.categories()).isEmpty();
    }
}
