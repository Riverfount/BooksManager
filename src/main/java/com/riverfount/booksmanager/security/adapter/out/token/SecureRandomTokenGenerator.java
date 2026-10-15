package com.riverfount.booksmanager.security.adapter.out.token;

import com.riverfount.booksmanager.security.application.port.out.OpaqueTokenGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Generates the text of a refresh token: 32 random bytes (256 bits) from a
 * cryptographically secure source, in URL-safe Base64 without padding (43
 * characters). Nothing in it can be guessed or derived.
 */
@Component
class SecureRandomTokenGenerator implements OpaqueTokenGenerator {

    private static final int BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        var bytes = new byte[BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
