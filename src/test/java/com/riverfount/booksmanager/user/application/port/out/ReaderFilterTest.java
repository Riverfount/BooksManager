package com.riverfount.booksmanager.user.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReaderFilterTest {

    @Test
    void noneShouldHaveNoFilters() {
        var filter = ReaderFilter.none();

        assertThat(filter.name()).isNull();
        assertThat(filter.document()).isNull();
        assertThat(filter.active()).isNull();
        assertThat(filter.blocked()).isNull();
    }

    @Test
    void shouldStripTheNameAndTreatBlankTextsAsAbsent() {
        assertThat(new ReaderFilter("  Maria ", null, null, null).name()).isEqualTo("Maria");
        assertThat(new ReaderFilter("   ", "  ", null, null).name()).isNull();
        assertThat(new ReaderFilter("   ", "  ", null, null).document()).isNull();
    }

    @Test
    void shouldNormalizeTheDocumentLikeTheRegistration() {
        assertThat(new ReaderFilter(null, "123.456.789-00", null, null).document()).isEqualTo("12345678900");
        assertThat(new ReaderFilter(null, " ab-12 ", null, null).document()).isEqualTo("AB12");
    }

    @Test
    void shouldTreatADocumentWithOnlyPunctuationAsAbsent() {
        assertThat(new ReaderFilter(null, ".-/", null, null).document()).isNull();
    }

    @Test
    void shouldKeepTheStateFilters() {
        var filter = new ReaderFilter(null, null, true, false);

        assertThat(filter.active()).isTrue();
        assertThat(filter.blocked()).isFalse();
    }
}
