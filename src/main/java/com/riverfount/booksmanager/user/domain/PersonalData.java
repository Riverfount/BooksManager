package com.riverfount.booksmanager.user.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.util.regex.Pattern;

/**
 * Format rules shared by the reader and the guardian (RNF11): names, e-mail
 * and phone. The {@code subject} is the Portuguese label used in the error
 * messages (for example, "do leitor" or "do responsável").
 */
final class PersonalData {

    static final int MAX_NAME_LENGTH = 150;
    static final int MAX_EMAIL_LENGTH = 254;
    static final int MAX_PHONE_LENGTH = 30;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");
    private static final Pattern PHONE_CHARACTERS = Pattern.compile("^\\+?[0-9 ()\\-]+$");
    private static final int MIN_PHONE_DIGITS = 8;
    private static final int MAX_PHONE_DIGITS = 15;

    private PersonalData() {
    }

    static void validateName(String name, String subject) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("o nome " + subject + " é obrigatório");
        }
        if (!name.equals(name.strip())) {
            throw new BusinessRuleException("o nome " + subject + " não pode ter espaços nas pontas");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BusinessRuleException(
                    "o nome " + subject + " deve ter até " + MAX_NAME_LENGTH + " caracteres");
        }
    }

    static void validateEmail(String email, String subject) {
        if (email == null || email.isBlank()) {
            throw new BusinessRuleException("o e-mail " + subject + " é obrigatório");
        }
        if (email.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(email).matches()) {
            throw new BusinessRuleException("o e-mail " + subject + " é inválido");
        }
    }

    /** The phone is optional: {@code null} is accepted; a value must have a valid format. */
    static void validateOptionalPhone(String phone, String subject) {
        if (phone == null) {
            return;
        }
        var digits = phone.chars().filter(Character::isDigit).count();
        if (phone.length() > MAX_PHONE_LENGTH
                || !PHONE_CHARACTERS.matcher(phone).matches()
                || digits < MIN_PHONE_DIGITS
                || digits > MAX_PHONE_DIGITS) {
            throw new BusinessRuleException("o telefone " + subject + " é inválido");
        }
    }
}
