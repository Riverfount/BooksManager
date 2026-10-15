package com.riverfount.booksmanager.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentMaskTest {

    @Test
    void shouldKeepOnlyTheLastTwoCharacters() {
        assertThat(DocumentMask.mask("12345678900")).isEqualTo("*********00");
        assertThat(DocumentMask.mask("AB123X")).isEqualTo("****3X");
    }

    @Test
    void shouldMaskEverythingInAShortDocument() {
        assertThat(DocumentMask.mask("1234")).isEqualTo("****");
        assertThat(DocumentMask.mask("12")).isEqualTo("**");
        assertThat(DocumentMask.mask("1")).isEqualTo("*");
    }

    @Test
    void shouldKeepTheSizeOfTheDocumentFromRevealingMoreThanTwoCharacters() {
        assertThat(DocumentMask.mask("12345")).isEqualTo("***45");
    }

    @Test
    void shouldNeverReturnTheOriginalDocument() {
        for (var document : new String[] {"1", "12", "123", "1234", "12345", "123456789012345"}) {
            assertThat(DocumentMask.mask(document)).isNotEqualTo(document);
        }
    }

    @Test
    void shouldReturnEmptyForANullOrEmptyDocument() {
        assertThat(DocumentMask.mask(null)).isEmpty();
        assertThat(DocumentMask.mask("")).isEmpty();
    }
}
