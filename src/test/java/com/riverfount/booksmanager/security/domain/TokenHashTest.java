package com.riverfount.booksmanager.security.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TokenHashTest {

    @Test
    void shouldHashWithSha256InLowercaseHexadecimal() {
        // known test vector of SHA-256
        assertThat(TokenHash.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void shouldAlwaysProduce64Characters() {
        assertThat(TokenHash.sha256Hex("a")).hasSize(TokenHash.LENGTH);
        assertThat(TokenHash.sha256Hex("a".repeat(500))).hasSize(TokenHash.LENGTH);
    }

    @Test
    void shouldBeDeterministicAndDifferentForDifferentTokens() {
        assertThat(TokenHash.sha256Hex("token-1")).isEqualTo(TokenHash.sha256Hex("token-1"));
        assertThat(TokenHash.sha256Hex("token-1")).isNotEqualTo(TokenHash.sha256Hex("token-2"));
    }

    @Test
    void theHashShouldNotContainTheToken() {
        assertThat(TokenHash.sha256Hex("super-secret-token")).doesNotContain("super-secret-token");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRefuseAMissingToken(String token) {
        assertThatThrownBy(() -> TokenHash.sha256Hex(token)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldRecognizeAWellFormedHash() {
        assertThat(TokenHash.isWellFormed(TokenHash.sha256Hex("x"))).isTrue();
        assertThat(TokenHash.isWellFormed("a".repeat(64))).isTrue();
        assertThat(TokenHash.isWellFormed("A".repeat(64))).isFalse();
        assertThat(TokenHash.isWellFormed("a".repeat(63))).isFalse();
        assertThat(TokenHash.isWellFormed("g".repeat(64))).isFalse();
        assertThat(TokenHash.isWellFormed(null)).isFalse();
    }
}
