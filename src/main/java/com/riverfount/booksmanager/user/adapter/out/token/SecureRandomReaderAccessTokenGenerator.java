package com.riverfount.booksmanager.user.adapter.out.token;

import com.riverfount.booksmanager.user.application.port.out.ReaderAccessTokenGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Generates the text of a reader access token: 32 random bytes (256 bits) from a cryptographically secure source,
 * in URL-safe Base64 without padding (43 characters), so it goes in a link as it is.
 */
@Component
class SecureRandomReaderAccessTokenGenerator implements ReaderAccessTokenGenerator {

    private static final int BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        var bytes = new byte[BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
