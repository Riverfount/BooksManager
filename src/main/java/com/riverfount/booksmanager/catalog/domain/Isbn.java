package com.riverfount.booksmanager.catalog.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;

/**
 * Book ISBN (RN01): immutable value object, normalized (no hyphens or
 * spaces, uppercase) and validated as ISBN-10 or ISBN-13, with the
 * correct check digit.
 */
public record Isbn(String value) {

    public Isbn(String value) {
        value = normalize(value);
        validate(value);
        this.value = value;
    }

    private static String normalize(String value) {
        if (value == null) {
            throw new BusinessRuleException("o ISBN é obrigatório");
        }
        return value.replace("-", "").replace(" ", "").toUpperCase();
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

    private static int digitValue(char character, boolean canBeX) {
        if (canBeX && character == 'X') {
            return 10;
        }
        if (Character.isDigit(character)) {
            return character - '0';
        }
        throw new BusinessRuleException("ISBN contém caractere inválido");
    }
}
