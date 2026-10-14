package com.riverfount.booksmanager.catalog.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BookFilterTest {

    @Test
    void noneShouldHaveNoFilter() {
        var filter = BookFilter.none();

        assertThat(filter.title()).isNull();
        assertThat(filter.author()).isNull();
        assertThat(filter.isbn()).isNull();
        assertThat(filter.categoryId()).isNull();
    }

    @Test
    void blankTextsShouldCountAsAbsent() {
        var filter = new BookFilter("   ", "", " \t ", 3L);

        assertThat(filter.title()).isNull();
        assertThat(filter.author()).isNull();
        assertThat(filter.isbn()).isNull();
        assertThat(filter.categoryId()).isEqualTo(3L);
    }

    @Test
    void shouldStripTheTextsOfTheSurroundingSpaces() {
        var filter = new BookFilter("  casmurro ", " machado", null, null);

        assertThat(filter.title()).isEqualTo("casmurro");
        assertThat(filter.author()).isEqualTo("machado");
    }

    @Test
    void shouldIgnoreHyphensAndSpacesInTheIsbn() {
        assertThat(new BookFilter(null, null, "978-85-333-0227", null).isbn()).isEqualTo("978853330227");
        assertThat(new BookFilter(null, null, "978 85 333", null).isbn()).isEqualTo("97885333");
    }

    @Test
    void shouldUppercaseTheCheckLetterOfTheIsbn() {
        assertThat(new BookFilter(null, null, "08044295x", null).isbn()).isEqualTo("08044295X");
    }

    @Test
    void anIsbnMadeOnlyOfSeparatorsShouldCountAsAbsent() {
        assertThat(new BookFilter(null, null, "- -", null).isbn()).isNull();
    }
}
