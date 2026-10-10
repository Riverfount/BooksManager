package com.riverfount.booksmanager.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;

class IsbnTest {

    @Test
    void shouldAcceptValidIsbn13() {
        var isbn = new Isbn("9788533302273");

        assertThat(isbn.value()).isEqualTo("9788533302273");
    }

    @Test
    void shouldNormalizeByRemovingHyphens() {
        var isbn = new Isbn("978-85-333-0227-3");

        assertThat(isbn.value()).isEqualTo("9788533302273");
    }

    @Test
    void shouldNormalizeByRemovingSpaces() {
        var isbn = new Isbn("978 85 333 0227 3");

        assertThat(isbn.value()).isEqualTo("9788533302273");
    }

    @Test
    void shouldAcceptValidIsbn10EndingInX() {
        var isbn = new Isbn("080442957X");

        assertThat(isbn.value()).isEqualTo("080442957X");
    }

    @Test
    void shouldNormalizeLowercaseXToUppercase() {
        var isbn = new Isbn("080442957x");

        assertThat(isbn.value()).isEqualTo("080442957X");
    }

    @Test
    void shouldNotAcceptNullIsbn() {
        assertThatThrownBy(() -> new Isbn(null)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotAcceptIsbn13WithWrongCheckDigit() {
        assertThatThrownBy(() -> new Isbn("9788533302270")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotAcceptIsbn10WithWrongCheckDigit() {
        assertThatThrownBy(() -> new Isbn("0804429571")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotAcceptWrongLength() {
        assertThatThrownBy(() -> new Isbn("123456")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotAcceptInvalidCharacters() {
        assertThatThrownBy(() -> new Isbn("97885333022AB")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldNotAcceptXOutsideTheLastPositionInIsbn10() {
        assertThatThrownBy(() -> new Isbn("X123456789")).isInstanceOf(BusinessRuleException.class);
    }
}
