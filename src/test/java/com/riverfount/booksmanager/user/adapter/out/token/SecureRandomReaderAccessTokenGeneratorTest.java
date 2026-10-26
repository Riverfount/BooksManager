package com.riverfount.booksmanager.user.adapter.out.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class SecureRandomReaderAccessTokenGeneratorTest {

    private final SecureRandomReaderAccessTokenGenerator generator = new SecureRandomReaderAccessTokenGenerator();

    @Test
    void shouldGenerateAUrlSafeTokenOf256BitsOfEntropy() {
        assertThat(generator.generate()).hasSize(43).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    void shouldNeverRepeat() {
        var seen = new HashSet<String>();

        for (var i = 0; i < 2000; i++) {
            assertThat(seen.add(generator.generate())).isTrue();
        }
    }
}
