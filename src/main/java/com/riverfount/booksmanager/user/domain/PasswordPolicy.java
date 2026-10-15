package com.riverfount.booksmanager.user.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Password rules of the system users (RF08, RNF07): from {@value #MIN_LENGTH}
 * to {@value #MAX_LENGTH} characters, with at least one uppercase letter, one
 * lowercase letter, one digit and one special character (anything that is
 * not a letter, a digit or a space). The rules apply to the plain password,
 * before it is hashed; the domain never stores or returns it.
 *
 * <p>BCrypt only uses the first {@value #MAX_BYTES} bytes, so a password
 * bigger than that is refused even if it fits the character limit (possible
 * with multi-byte characters).
 *
 * <p>The error message lists the rules that were not met and never repeats
 * the password.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 25;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BusinessRuleException("a senha é obrigatória");
        }
        var unmet = new ArrayList<String>();
        var length = rawPassword.codePointCount(0, rawPassword.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            unmet.add("ter entre " + MIN_LENGTH + " e " + MAX_LENGTH + " caracteres");
        } else if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            unmet.add("ter no máximo " + MAX_BYTES + " bytes (UTF-8)");
        }
        if (rawPassword.codePoints().noneMatch(Character::isUpperCase)) {
            unmet.add("ter ao menos uma letra maiúscula");
        }
        if (rawPassword.codePoints().noneMatch(Character::isLowerCase)) {
            unmet.add("ter ao menos uma letra minúscula");
        }
        if (rawPassword.codePoints().noneMatch(Character::isDigit)) {
            unmet.add("ter ao menos um número");
        }
        if (rawPassword.codePoints().noneMatch(PasswordPolicy::isSpecial)) {
            unmet.add("ter ao menos um caractere especial");
        }
        if (!unmet.isEmpty()) {
            throw new BusinessRuleException("a senha não atende às regras: deve " + String.join("; deve ", unmet));
        }
    }

    private static boolean isSpecial(int codePoint) {
        return !Character.isLetterOrDigit(codePoint) && !Character.isWhitespace(codePoint);
    }
}
