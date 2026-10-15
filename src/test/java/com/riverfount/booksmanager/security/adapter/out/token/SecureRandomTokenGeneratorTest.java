package com.riverfount.booksmanager.security.adapter.out.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class SecureRandomTokenGeneratorTest {

    private final SecureRandomTokenGenerator generator = new SecureRandomTokenGenerator();

    @Test
    void shouldGenerateAUrlSafeTokenOf256BitsOfEntropy() {
        var token = generator.generate();

        // 32 random bytes in Base64 without padding
        assertThat(token).hasSize(43).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    void shouldNeverRepeat() {
        var seen = new HashSet<String>();

        for (var i = 0; i < 2000; i++) {
            assertThat(seen.add(generator.generate())).isTrue();
        }
    }
}
