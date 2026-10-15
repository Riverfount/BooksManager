package com.riverfount.booksmanager.user.adapter.out.password;

import static org.assertj.core.api.Assertions.assertThat;

import com.riverfount.booksmanager.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class BCryptPasswordHasherTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

    @Test
    void shouldProduceABcryptHashThatChecksAgainstThePassword() {
        var hash = hasher.hash("s3cret-pass");

        assertThat(hash).startsWith("$2");
        assertThat(encoder.matches("s3cret-pass", hash)).isTrue();
        assertThat(encoder.matches("wrong", hash)).isFalse();
    }

    @Test
    void shouldNeverReturnThePasswordItself() {
        assertThat(hasher.hash("s3cret-pass")).doesNotContain("s3cret-pass");
    }

    @Test
    void shouldSaltEachHash() {
        assertThat(hasher.hash("s3cret-pass")).isNotEqualTo(hasher.hash("s3cret-pass"));
    }

    @Test
    void theHashShouldFitTheColumnOfTheUser() {
        assertThat(hasher.hash("a-very-long-password".repeat(3)).length())
                .isLessThanOrEqualTo(User.MAX_PASSWORD_HASH_LENGTH);
    }

    // matches (login, issue #103)

    @Test
    void matchesShouldAcceptTheRightPasswordAndRefuseAWrongOne() {
        var hash = hasher.hash("S3cret-Pass!");

        assertThat(hasher.matches("S3cret-Pass!", hash)).isTrue();
        assertThat(hasher.matches("S3cret-Pass", hash)).isFalse();
        assertThat(hasher.matches("s3cret-pass!", hash)).isFalse();
        assertThat(hasher.matches("", hash)).isFalse();
    }

    @Test
    void matchesShouldCheckAHashMadeByAnotherInstance() {
        var hash = encoder.encode("S3cret-Pass!");

        assertThat(hasher.matches("S3cret-Pass!", hash)).isTrue();
    }

    @Test
    void matchesShouldRefuseAMalformedOrMissingHashInsteadOfFailing() {
        assertThat(hasher.matches("S3cret-Pass!", "not-a-bcrypt-hash")).isFalse();
        assertThat(hasher.matches("S3cret-Pass!", "")).isFalse();
        assertThat(hasher.matches("S3cret-Pass!", null)).isFalse();
    }

    @Test
    void matchesShouldTreatAMissingPasswordAsNotMatching() {
        var hash = hasher.hash("S3cret-Pass!");

        assertThat(hasher.matches(null, hash)).isFalse();
    }
}
