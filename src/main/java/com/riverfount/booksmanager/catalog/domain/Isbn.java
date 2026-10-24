package com.riverfount.booksmanager.catalog.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.util.Locale;

/**
 * Book ISBN (RN01): immutable value object, normalized (no hyphens or
 * spaces, uppercase) and validated as ISBN-10 or ISBN-13, with the
 * correct check digit. An ISBN-10 is converted to the ISBN-13 of the
 * same book (prefix 978 and a new check digit), so the value is always
 * the ISBN-13: the two forms of one book are the same ISBN, and the
 * uniqueness of the ISBN cannot be gone around by writing it the other way.
 */
public record Isbn(String value) {

    public Isbn(String value) {
        value = normalize(value);
        validate(value);
        this.value = value.length() == 10 ? toIsbn13(value) : value;
    }

    private static String normalize(String value) {
        if (value == null) {
            throw new BusinessRuleException("o ISBN é obrigatório");
        }
        return value.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private static void validate(String value) {
        switch (value.length()) {
            case 10 -> validateIsbn10(value);
            case 13 -> validateIsbn13(value);
            default -> throw new BusinessRuleException("ISBN deve ter 10 ou 13 dígitos");
        }
    }

    private static void validateIsbn10(String value) {
        var sum = 0;
        for (var i = 0; i < 10; i++) {
            sum += digitValue(value.charAt(i), i == 9) * (10 - i);
        }
        if (sum % 11 != 0) {
            throw new BusinessRuleException("dígito verificador do ISBN-10 inválido");
        }
    }

    private static void validateIsbn13(String value) {
        var sum = 0;
        for (var i = 0; i < 13; i++) {
            var weight = (i % 2 == 0) ? 1 : 3;
            sum += digitValue(value.charAt(i), false) * weight;
        }
        if (sum % 10 != 0) {
            throw new BusinessRuleException("dígito verificador do ISBN-13 inválido");
        }
    }

    /** 978 + the first nine digits of the ISBN-10 + the new check digit (the last digit of the ISBN-10 is dropped). */
    private static String toIsbn13(String isbn10) {
        var withoutCheckDigit = "978" + isbn10.substring(0, 9);
        var sum = 0;
        for (var i = 0; i < 12; i++) {
            sum += (withoutCheckDigit.charAt(i) - '0') * ((i % 2 == 0) ? 1 : 3);
        }
        return withoutCheckDigit + (10 - sum % 10) % 10;
    }

    private static int digitValue(char character, boolean canBeX) {
        if (canBeX && character == 'X') {
            return 10;
        }
        // not Character.isDigit: it accepts the digits of other scripts, and "character - '0'" would be wrong for them
        if (character >= '0' && character <= '9') {
            return character - '0';
        }
        throw new BusinessRuleException("ISBN contém caractere inválido");
    }
}
