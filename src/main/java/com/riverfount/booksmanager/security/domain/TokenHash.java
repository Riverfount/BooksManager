package com.riverfount.booksmanager.security.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Hash of an opaque token (RF08, RNF07): SHA-256 in lowercase hexadecimal,
 * 64 characters. A fast hash is enough here, unlike for a password, because
 * the token is random and has high entropy, so there is nothing to guess; it
 * also lets the token be found by its hash with an indexed lookup.
 */
public final class TokenHash {

    public static final int LENGTH = 64;

    private static final Pattern WELL_FORMED = Pattern.compile("^[0-9a-f]{" + LENGTH + "}$");

    private TokenHash() {
    }

    public static String sha256Hex(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessRuleException("o token é obrigatório");
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required by every Java platform
            throw new IllegalStateException(e);
        }
    }

    /** Whether the text has the shape of a hash made by this class (and so is not a plain token). */
    public static boolean isWellFormed(String value) {
        return value != null && WELL_FORMED.matcher(value).matches();
    }
}
