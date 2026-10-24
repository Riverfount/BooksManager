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
    void shouldConvertIsbn10ToIsbn13() {
        var isbn = new Isbn("8533302274");

        assertThat(isbn.value()).isEqualTo("9788533302273");
    }

    @Test
    void shouldConvertIsbn10EndingInXToIsbn13() {
        var isbn = new Isbn("080442957X");

        assertThat(isbn.value()).isEqualTo("9780804429573");
    }

    @Test
    void shouldAcceptLowercaseXInIsbn10() {
        var isbn = new Isbn("080442957x");

        assertThat(isbn.value()).isEqualTo("9780804429573");
    }

    @Test
    void shouldConvertIsbn10WithHyphens() {
        assertThat(new Isbn("85-333-0227-4").value()).isEqualTo("9788533302273");
    }

    @Test
    void shouldGiveTheSameIsbnForTheTenAndTheThirteenDigitFormsOfTheSameBook() {
        assertThat(new Isbn("8533302274")).isEqualTo(new Isbn("978-85-333-0227-3"));
    }

    @Test
    void shouldConvertIsbn10WhoseIsbn13CheckDigitIsZero() {
        // the check digit of the ISBN-13 is (10 - sum % 10) % 10, so a sum that is a multiple of 10 gives 0, not 10
        assertThat(new Isbn("0000000043").value()).isEqualTo("9780000000040");
    }

    @Test
    void shouldNotAcceptDigitsOfOtherScripts() {
        // Arabic-Indic digits: Character.isDigit accepts them and the sum would be computed with the wrong values
        assertThatThrownBy(() -> new Isbn("\u0669\u0667\u0668\u0668\u0665\u0663\u0663\u0663\u0660\u0662\u0662\u0667\u0663"))
                .isInstanceOf(BusinessRuleException.class);
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
